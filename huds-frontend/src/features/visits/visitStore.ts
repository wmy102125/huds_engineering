import { hasPermission, PERMISSION } from '../../api/permissions'
import type { CurrentUser, VisitKind, VisitPayload, VisitStatus, VisitTransitionPayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalAppliance, LocalCase, LocalCasePart, LocalCustomer, LocalModel, LocalUser, LocalVisit } from '../../db/db'
import { enqueueMutation, findCreateMutationId, findLastUnsentMutationId, outboxOfRecord } from '../../sync/outbox'
import { isFinished, statusLabel } from '../cases/caseLabels'
import { isPartWaiting, listParts } from '../cases/casePartStore'
import { describeAppliance } from '../cases/caseStore'
import { fromDateTimeInput, isVisitFinished, isVisitOpen, visitStatusLabel } from './visitLabels'
import { visitSteps } from './visitStateMachine'
import type { VisitStep } from './visitStateMachine'

/**
 * Same pattern as the other stores: the local record and the outbox change are saved in one transaction.
 * A visit step is shown at once and sent later; the server may still refuse it.
 */

/**
 * Why a visit of this case cannot be started now, or null when it can.
 * Starting a visit moves the case on by itself (Open -> Diagnosing, Waiting for parts -> In repair).
 */
export function visitStartBlockReason(caseRecord: LocalCase | undefined, parts: LocalCasePart[]): string | null {
  if (!caseRecord) {
    return 'The case of this visit is not on this device.'
  }
  switch (caseRecord.workflowStatus) {
    case 'OPEN':
    case 'DIAGNOSING':
    case 'IN_REPAIR':
      return null
    case 'PENDING_PARTS':
      return parts.some((part) => isPartWaiting(part)) ? 'Some parts of this case have not arrived yet.' : null
    case 'PENDING_AUTHORIZATION':
      return 'The case is waiting for the approval of the customer. Record the answer on the case first.'
    default:
      return `The case is "${statusLabel(caseRecord.workflowStatus)}". A visit cannot start now.`
  }
}

export function canAssignVisits(user: CurrentUser): boolean {
  return hasPermission(user, PERMISSION.VISIT_ASSIGN)
}

/** A technician has completed a visit of this case: only then may the technician plan the next one. */
export function hasCompletedVisitOf(user: CurrentUser, visits: LocalVisit[]): boolean {
  return visits.some((visit) => visit.technicianId === user.userId && visit.visitStatus === 'COMPLETED')
}

/**
 * Who may plan a visit of this case: an administrator, or a technician who completed an earlier visit of the case
 * (a follow-up visit, for the technician in person).
 */
export function canPlanVisit(user: CurrentUser, visits: LocalVisit[]): boolean {
  return canAssignVisits(user) || hasCompletedVisitOf(user, visits)
}

/** One step in the history of a visit, from the server or still waiting to be sent. */
export interface VisitTimelineEntry {
  key: string
  fromState: VisitStatus | null
  toState: VisitStatus
  reason: string
  actorName: string
  at: string
  waiting: boolean
}

export interface VisitDetail {
  visit: LocalVisit
  caseRecord: LocalCase | undefined
  customer: LocalCustomer | undefined
  appliance: LocalAppliance | undefined
  model: LocalModel | undefined
  timeline: VisitTimelineEntry[]
  /** Newest first. */
  steps: VisitStep[]
  /** Another visit of the same case that is on site or paused (a visit cannot start while there is one). */
  otherOpenVisit: LocalVisit | undefined
}

/** One line of a visit list: the visit with the names that are needed to read it. */
export interface VisitRow {
  visit: LocalVisit
  caseNo: string
  customerName: string
  applianceText: string
}

/** Open visits first, then planned ones by time, then finished ones with the newest on top. */
function compareVisits(a: LocalVisit, b: LocalVisit): number {
  const rank = (visit: LocalVisit) => (isVisitOpen(visit.visitStatus) ? 0 : visit.visitStatus === 'PLANNED' ? 1 : 2)
  if (rank(a) !== rank(b)) {
    return rank(a) - rank(b)
  }
  if (rank(a) === 1) {
    // A visit without a time goes last.
    return (a.scheduledAt ?? '9999').localeCompare(b.scheduledAt ?? '9999')
  }
  return (b.endedAt ?? b.startedAt ?? b.updatedAt ?? '').localeCompare(a.endedAt ?? a.startedAt ?? a.updatedAt ?? '')
}

export async function listVisitsOfCase(diagnosticCaseId: string): Promise<LocalVisit[]> {
  const visits = await db().visits.where('diagnosticCaseId').equals(diagnosticCaseId).toArray()
  return visits.sort(compareVisits)
}

/** The visit of a case that is not completed or cancelled yet (planned, on site or paused), if there is one. A case has at most one. */
export function findUnfinishedVisit(visits: LocalVisit[]): LocalVisit | undefined {
  return visits.find((visit) => !isVisitFinished(visit.visitStatus))
}

/** The visit of a case that is on site or paused, if there is one (there is at most one). */
export function findOpenVisit(visits: LocalVisit[]): LocalVisit | undefined {
  return visits.find((visit) => isVisitOpen(visit.visitStatus))
}

/** Only users with the role TECHNICIAN can be given a visit. */
const TECHNICIAN_ROLE = 'TECHNICIAN'

/** The people a visit can be given to, sorted by name. */
export async function listTechnicians(): Promise<LocalUser[]> {
  const users = (await db().users.toArray()).filter((item) => item.role === TECHNICIAN_ROLE)
  return users.sort((a, b) => a.userName.localeCompare(b.userName))
}

async function technicianName(technicianId: string, user: CurrentUser): Promise<string> {
  if (technicianId === user.userId) {
    return user.userName
  }
  return (await db().users.get(technicianId))?.userName ?? ''
}

export interface VisitForm {
  technicianId: string
  /** Empty: the server decides. */
  visitKind: VisitKind | ''
  /** A datetime-local value or empty. */
  scheduledAt: string
  notes: string
}

/**
 * Plans a visit. An administrator may plan any visit for any technician. A technician may only plan a follow-up visit
 * for himself or herself, and only after completing an earlier visit of the case. A new visit is always PLANNED.
 */
export async function planVisit(caseRecord: LocalCase, user: CurrentUser, form: VisitForm): Promise<string> {
  if (isFinished(caseRecord.workflowStatus)) {
    throw new Error('A finished case cannot get a new visit.')
  }
  // A case on hold waits for a decision: it has to be resumed before a visit is planned.
  if (caseRecord.workflowStatus === 'ON_HOLD') {
    throw new Error('The case is on hold. Resume it before planning a visit.')
  }
  // One visit at a time: the server refuses a new visit while another one is planned, on site or paused.
  const caseVisits = await listVisitsOfCase(caseRecord.diagnosticCaseId)
  if (!canAssignVisits(user)) {
    if (!hasCompletedVisitOf(user, caseVisits)) {
      throw new Error('You can plan the next visit after you have completed a visit of this case.')
    }
    if (form.technicianId !== user.userId) {
      throw new Error('You can only plan a visit for yourself.')
    }
    if (form.visitKind !== '' && form.visitKind !== 'FOLLOW_UP') {
      throw new Error('You can only plan a follow-up visit.')
    }
  }
  const existingKinds = caseVisits.filter((item) => item.visitStatus !== 'CANCELLED').length
  const unfinished = findUnfinishedVisit(caseVisits)
  if (unfinished) {
    throw new Error(`This case already has a visit that is "${visitStatusLabel(unfinished.visitStatus)}". Complete or cancel it before planning another.`)
  }
  if (form.technicianId === '') {
    throw new Error('Please choose a technician.')
  }
  const payload: VisitPayload = {
    diagnosticCaseId: caseRecord.diagnosticCaseId,
    technicianId: form.technicianId,
    visitKind: form.visitKind === '' ? null : form.visitKind,
    scheduledAt: fromDateTimeInput(form.scheduledAt),
    notes: form.notes.trim(),
  }
  const serviceVisitId = crypto.randomUUID()
  const name = await technicianName(form.technicianId, user)
  const database = db()
  await database.transaction('rw', database.visits, database.outbox, async () => {
    await database.visits.add({
      serviceVisitId,
      diagnosticCaseId: payload.diagnosticCaseId,
      technicianId: payload.technicianId,
      technicianName: name,
      visitStatus: 'PLANNED',
      // Same rule as the server: the first visit is FIRST, later ones FOLLOW_UP. The download brings the server's answer.
      visitKind: payload.visitKind ?? (existingKinds > 0 ? 'FOLLOW_UP' : 'FIRST'),
      scheduledAt: payload.scheduledAt,
      startedAt: null,
      endedAt: null,
      notes: payload.notes,
      syncState: 'PENDING',
    })
    // The visit has to wait for its case when the server does not have the case yet.
    const caseCreate = await findCreateMutationId(payload.diagnosticCaseId)
    await enqueueMutation({
      entityType: 'SERVICE_VISIT',
      entityId: serviceVisitId,
      operation: 'CREATE',
      payload,
      dependsOn: caseCreate ? [caseCreate] : [],
    })
  })
  return serviceVisitId
}

/** Changes the technician, the planned time or the notes of a planned visit (administrators only). */
export async function updateVisit(visit: LocalVisit, user: CurrentUser, form: VisitForm): Promise<void> {
  if (!canAssignVisits(user)) {
    throw new Error('Only an administrator can change a planned visit.')
  }
  if (visit.visitStatus !== 'PLANNED') {
    throw new Error('Only a planned visit can be changed.')
  }
  if (form.technicianId === '') {
    throw new Error('Please choose a technician.')
  }
  const payload: VisitPayload = {
    diagnosticCaseId: visit.diagnosticCaseId,
    technicianId: form.technicianId,
    visitKind: form.visitKind === '' ? null : form.visitKind,
    scheduledAt: fromDateTimeInput(form.scheduledAt),
    notes: form.notes.trim(),
  }
  const name = await technicianName(form.technicianId, user)
  const database = db()
  await database.transaction('rw', database.visits, database.outbox, async () => {
    await database.visits.update(visit.serviceVisitId, {
      technicianId: payload.technicianId,
      technicianName: name,
      ...(payload.visitKind ? { visitKind: payload.visitKind } : {}),
      scheduledAt: payload.scheduledAt,
      notes: payload.notes,
      syncState: 'PENDING',
    })
    await enqueueMutation({
      entityType: 'SERVICE_VISIT',
      entityId: visit.serviceVisitId,
      operation: 'UPDATE',
      rowVersion: visit.rowVersion,
      payload,
    })
  })
}

export interface VisitStepInput {
  toState: VisitStatus
  /** The reason of a pause, or an optional note for the other steps. */
  reason: string
}

/** Moves the visit to another state: start, pause, resume, complete or cancel. */
export async function transitionVisit(visit: LocalVisit, user: CurrentUser, input: VisitStepInput): Promise<void> {
  const step = visitSteps(visit.visitStatus).find((candidate) => candidate.toState === input.toState)
  if (!step) {
    throw new Error(`A visit that is "${visitStatusLabel(visit.visitStatus)}" cannot move to "${visitStatusLabel(input.toState)}".`)
  }
  const admin = canAssignVisits(user)
  if (step.adminOnly && !admin) {
    throw new Error('Only an administrator can cancel a visit.')
  }
  if (!step.adminOnly && visit.technicianId !== user.userId && !admin) {
    throw new Error('This visit is assigned to another technician.')
  }
  const reason = input.reason.trim()
  if (step.needsReason && reason === '') {
    throw new Error('Please say why.')
  }

  const database = db()
  const starting = visit.visitStatus === 'PLANNED' && input.toState === 'IN_PROGRESS'
  if (starting) {
    const blocked = visitStartBlockReason(await database.cases.get(visit.diagnosticCaseId), await listParts(visit.diagnosticCaseId))
    if (blocked) {
      throw new Error(blocked)
    }
    const others = await listVisitsOfCase(visit.diagnosticCaseId)
    if (others.some((other) => other.serviceVisitId !== visit.serviceVisitId && isVisitOpen(other.visitStatus))) {
      throw new Error('Another visit of this case is still going on. Complete it first.')
    }
  }

  // A visit cannot be completed before its safety checks are recorded.
  const needsChecks = input.toState === 'COMPLETED'
  if (needsChecks && !(await database.visitSafetyChecks.get(visit.serviceVisitId))) {
    throw new Error('Record the safety checks first.')
  }

  // The final status of the completion record decides what happens to the case, so it has to be filled in first.
  if (input.toState === 'COMPLETED') {
    const record = await database.visitCompletions.where('serviceVisitId').equals(visit.serviceVisitId).first()
    if (!record || !record.finalStatus) {
      throw new Error('Choose the final status in the completion record first.')
    }
  }
  const payload: VisitTransitionPayload = { toState: input.toState, notes: reason === '' ? null : reason }
  const now = new Date().toISOString()
  await database.transaction('rw', database.visits, database.visitCompletions, database.outbox, async () => {
    // The server refuses the completion without the safety checks, so it waits for them when they are not sent yet.
    const checksCreate = needsChecks ? await findLastUnsentMutationId('VISIT_SAFETY_CHECK', visit.serviceVisitId) : undefined
    // The completion record can only be written while the visit is on site, so the completion waits for it as well.
    const completionRecord = needsChecks ? await database.visitCompletions.where('serviceVisitId').equals(visit.serviceVisitId).first() : undefined
    const completionWrite = completionRecord ? await findLastUnsentMutationId('VISIT_COMPLETION', completionRecord.visitCompletionRecordId) : undefined
    await database.visits.update(visit.serviceVisitId, {
      visitStatus: input.toState,
      startedAt: starting ? now : visit.startedAt,
      endedAt: input.toState === 'COMPLETED' || input.toState === 'CANCELLED' ? now : visit.endedAt,
      syncState: 'PENDING',
    })
    await enqueueMutation({
      entityType: 'SERVICE_VISIT',
      entityId: visit.serviceVisitId,
      operation: 'TRANSITION',
      rowVersion: visit.rowVersion,
      payload,
      dependsOn: [checksCreate, completionWrite].filter((id): id is string => id !== undefined),
    })
  })
}

/** A visit with everything the detail page shows. Returns undefined when the visit is not on this device. */
export async function getVisitDetail(serviceVisitId: string): Promise<VisitDetail | undefined> {
  const database = db()
  const visit = await database.visits.get(serviceVisitId)
  if (!visit) {
    return undefined
  }
  const caseRecord = await database.cases.get(visit.diagnosticCaseId)
  const customer = caseRecord ? await database.customers.get(caseRecord.customerId) : undefined
  const appliance = caseRecord ? await database.appliances.get(caseRecord.applianceId) : undefined
  const model = appliance ? await database.models.get(appliance.applianceModelId) : undefined

  const events = (await database.stateEvents.where('entityId').equals(serviceVisitId).toArray()).filter((event) => event.entityType === 'VISIT')
  const waiting = (await outboxOfRecord('SERVICE_VISIT', serviceVisitId)).filter((item) => item.status !== 'REJECTED')

  const timeline: VisitTimelineEntry[] = events
    .sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))
    .map((event) => ({
      key: event.stateEventId,
      fromState: event.fromState as VisitStatus | null,
      toState: event.toState as VisitStatus,
      reason: event.reason,
      actorName: event.actor?.userName ?? '',
      at: event.occurredAt,
      waiting: false,
    }))
  const create = waiting.find((item) => item.operation === 'CREATE')
  if (create && !timeline.some((entry) => entry.toState === 'PLANNED')) {
    // The visit itself has not been accepted yet, so there is no history entry for it.
    timeline.unshift({ key: 'planned', fromState: null, toState: 'PLANNED', reason: '', actorName: '', at: create.clientRecordedAt, waiting: true })
  }
  // The steps made on this device that are still on their way. Each one starts where the one before it ended.
  let previous: VisitStatus = timeline.at(-1)?.toState ?? 'PLANNED'
  for (const item of waiting.filter((candidate) => candidate.operation === 'TRANSITION')) {
    const payload = item.payload as VisitTransitionPayload
    timeline.push({
      key: `waiting-${item.seq}`,
      fromState: previous,
      toState: payload.toState,
      reason: payload.notes ?? '',
      actorName: '',
      at: item.clientRecordedAt,
      waiting: true,
    })
    previous = payload.toState
  }

  return {
    visit,
    caseRecord,
    customer,
    appliance,
    model,
    timeline,
    steps: visitSteps(visit.visitStatus),
    otherOpenVisit: (await listVisitsOfCase(visit.diagnosticCaseId)).find(
      (other) => other.serviceVisitId !== serviceVisitId && isVisitOpen(other.visitStatus),
    ),
  }
}

/** Visits for the "My visits" page: the user's own, or everybody's (administrators). Names are read with one lookup per table. */
export async function listVisitRows(userId: string | null): Promise<VisitRow[]> {
  const database = db()
  const all = await database.visits.toArray()
  const visits = (userId === null ? all : all.filter((visit) => visit.technicianId === userId)).sort(compareVisits)

  const cases = await database.cases.bulkGet([...new Set(visits.map((visit) => visit.diagnosticCaseId))])
  const caseById = new Map(cases.filter((item) => item !== undefined).map((item) => [item.diagnosticCaseId, item]))
  const customers = await database.customers.bulkGet([...new Set([...caseById.values()].map((item) => item.customerId))])
  const customerById = new Map(customers.filter((item) => item !== undefined).map((item) => [item.customerId, item]))
  const appliances = await database.appliances.bulkGet([...new Set([...caseById.values()].map((item) => item.applianceId))])
  const applianceById = new Map(appliances.filter((item) => item !== undefined).map((item) => [item.applianceId, item]))
  const models = await database.models.bulkGet([...new Set(appliances.map((item) => item?.applianceModelId ?? ''))])
  const modelById = new Map(models.filter((item) => item !== undefined).map((item) => [item.applianceModelId, item]))

  return visits.map((visit) => {
    const caseRecord = caseById.get(visit.diagnosticCaseId)
    const appliance = caseRecord ? applianceById.get(caseRecord.applianceId) : undefined
    return {
      visit,
      caseNo: caseRecord?.caseNo ?? '',
      customerName: (caseRecord && customerById.get(caseRecord.customerId)?.customerName) || 'Unknown customer',
      applianceText: describeAppliance(appliance, appliance ? modelById.get(appliance.applianceModelId) : undefined),
    }
  })
}
