import type { CaseDisposition, CasePayload, CaseStatus, CaseTransitionPayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalAddress, LocalAppliance, LocalCase, LocalCustomer, LocalModel } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'
import { applianceTypeLabel } from '../appliances/applianceTypes'
import { isFinished, statusLabel } from './caseLabels'
import { insertErrorCode } from './caseErrorCodeStore'
import type { ErrorCodeForm } from './caseErrorCodeStore'
import { timingFrom } from './CaseClassificationFields'
import type { ClassificationForm } from './CaseClassificationFields'
import { transitionOptions } from './caseStateMachine'
import type { TransitionOption } from './caseStateMachine'

/**
 * Same pattern as the other stores: the local record and the outbox change are saved in one transaction.
 * The case number is given by the server, so a new case shows no number until it has been sent and downloaded.
 */

export interface CaseForm {
  customerId: string
  serviceAddressId: string
  applianceId: string
  reportedSymptoms: string
  classification: ClassificationForm
  /** Error codes read from the appliance; each one is sent as a record of its own. */
  errorCodes: ErrorCodeForm[]
}

/** One line of a case list: the case with the names that are needed to read it. */
export interface CaseRow {
  caseRecord: LocalCase
  customerName: string
  customerPhone: string
  applianceText: string
}

/** One step in the history of a case, from the server or still waiting to be sent. */
export interface TimelineEntry {
  key: string
  fromState: CaseStatus | null
  toState: CaseStatus
  reason: string
  actorName: string
  at: string
  waiting: boolean
}

export interface CaseDetail {
  caseRecord: LocalCase
  customer: LocalCustomer | undefined
  address: LocalAddress | undefined
  appliance: LocalAppliance | undefined
  model: LocalModel | undefined
  timeline: TimelineEntry[]
  /** The state the case was in before it was put on hold (only for a case on hold). */
  resumeTo: CaseStatus | null
  options: TransitionOption[]
}

/** "Washer Samsung WF45 (serial 123)" */
export function describeAppliance(appliance: LocalAppliance | undefined, model: LocalModel | undefined): string {
  if (!appliance || !model) {
    return 'Unknown appliance'
  }
  const text = [applianceTypeLabel(model.applianceType), model.brand, model.modelNo].filter(Boolean).join(' ')
  return appliance.serialNo ? `${text} (serial ${appliance.serialNo})` : text
}

/**
 * Newest case first: by the time the server created it, then by case number, then by id.
 * A case that the server has not accepted yet has no creation time; the time it was opened on this device is used, so it stays on top.
 */
function compareNewestFirst(a: LocalCase, b: LocalCase): number {
  return (
    (b.createdAt ?? b.openedAt).localeCompare(a.createdAt ?? a.openedAt) ||
    b.caseNo.localeCompare(a.caseNo) ||
    b.diagnosticCaseId.localeCompare(a.diagnosticCaseId)
  )
}

/** All cases, newest first. Names are read with one lookup per table, not with joins. */
export async function listCaseRows(): Promise<CaseRow[]> {
  const database = db()
  const cases = await database.cases.toArray()
  const customers = await database.customers.bulkGet([...new Set(cases.map((item) => item.customerId))])
  const appliances = await database.appliances.bulkGet([...new Set(cases.map((item) => item.applianceId))])
  const models = await database.models.bulkGet([...new Set(appliances.map((item) => item?.applianceModelId ?? ''))])

  const customerById = new Map(customers.filter((item) => item !== undefined).map((item) => [item.customerId, item]))
  const applianceById = new Map(appliances.filter((item) => item !== undefined).map((item) => [item.applianceId, item]))
  const modelById = new Map(models.filter((item) => item !== undefined).map((item) => [item.applianceModelId, item]))

  return cases
    .map((caseRecord) => {
      const customer = customerById.get(caseRecord.customerId)
      const appliance = applianceById.get(caseRecord.applianceId)
      return {
        caseRecord,
        customerName: customer?.customerName ?? 'Unknown customer',
        customerPhone: customer?.phone ?? '',
        applianceText: describeAppliance(appliance, appliance ? modelById.get(appliance.applianceModelId) : undefined),
      }
    })
    .sort((a, b) => compareNewestFirst(a.caseRecord, b.caseRecord))
}

/** A case with everything the detail page shows. Returns undefined when the case is not on this device. */
export async function getCaseDetail(diagnosticCaseId: string): Promise<CaseDetail | undefined> {
  const database = db()
  const caseRecord = await database.cases.get(diagnosticCaseId)
  if (!caseRecord) {
    return undefined
  }
  const customer = await database.customers.get(caseRecord.customerId)
  const address = await database.addresses.get(caseRecord.serviceAddressId)
  const appliance = await database.appliances.get(caseRecord.applianceId)
  const model = appliance ? await database.models.get(appliance.applianceModelId) : undefined
  const events = await database.stateEvents.where('entityId').equals(diagnosticCaseId).toArray()
  const waitingSteps = (await database.outbox.where('entityId').equals(diagnosticCaseId).sortBy('seq')).filter(
    (item) => item.operation === 'TRANSITION' && item.status !== 'REJECTED',
  )

  const timeline: TimelineEntry[] = events
    .sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))
    .map((event) => ({
      key: event.stateEventId,
      // The states of a case event are case states (the events of other entities have other ids).
      fromState: event.fromState as CaseStatus | null,
      toState: event.toState as CaseStatus,
      reason: event.reason,
      actorName: event.actor?.userName ?? '',
      at: event.occurredAt,
      waiting: false,
    }))
  if (caseRecord.syncState !== 'SYNCED' && !timeline.some((entry) => entry.toState === 'OPEN')) {
    // The case itself has not been accepted yet, so there is no history entry for it.
    timeline.unshift({ key: 'opened', fromState: null, toState: 'OPEN', reason: '', actorName: '', at: caseRecord.openedAt, waiting: true })
  }
  // The steps that were made on this device and are still on their way. Each one starts where the one before it ended.
  let previous: CaseStatus = timeline.at(-1)?.toState ?? 'OPEN'
  for (const item of waitingSteps) {
    const payload = item.payload as CaseTransitionPayload
    timeline.push({
      key: `waiting-${item.seq}`,
      fromState: previous,
      toState: payload.toState,
      reason: payload.reason ?? '',
      actorName: '',
      at: item.clientRecordedAt,
      waiting: true,
    })
    previous = payload.toState
  }

  let resumeTo: CaseStatus | null = null
  if (caseRecord.workflowStatus === 'ON_HOLD') {
    resumeTo = [...timeline].reverse().find((entry) => entry.toState === 'ON_HOLD')?.fromState ?? null
  }
  return {
    caseRecord,
    customer,
    address,
    appliance,
    model,
    timeline,
    resumeTo,
    options: transitionOptions(caseRecord.workflowStatus, resumeTo),
  }
}

/** Cases of an appliance that are not finished yet (the new case form refuses to open another one). */
export async function listUnfinishedCasesOfAppliance(applianceId: string): Promise<LocalCase[]> {
  const cases = await db().cases.where('applianceId').equals(applianceId).toArray()
  return cases.filter((item) => !isFinished(item.workflowStatus))
}

/** Every case of an appliance, newest first (the number is empty while a new case waits to be sent, so the open time decides). */
export async function listCasesOfAppliance(applianceId: string): Promise<LocalCase[]> {
  const cases = await db().cases.where('applianceId').equals(applianceId).toArray()
  return cases.sort((a, b) => b.openedAt.localeCompare(a.openedAt))
}

/** Opens a new case. Returns its id. */
export async function createCase(form: CaseForm): Promise<string> {
  const database = db()
  const diagnosticCaseId = crypto.randomUUID()
  const address = await database.addresses.get(form.serviceAddressId)
  if (!address || address.customerId !== form.customerId) {
    throw new Error('The address does not belong to this customer.')
  }
  const appliance = await database.appliances.get(form.applianceId)
  if (!appliance || appliance.customerId !== form.customerId) {
    throw new Error('The appliance does not belong to this customer.')
  }
  // One unfinished case per appliance: the server refuses a second one, so do not even try.
  const unfinished = await listUnfinishedCasesOfAppliance(form.applianceId)
  if (unfinished.length > 0) {
    throw new Error(`This appliance already has an unfinished case (${unfinished[0].caseNo || 'number pending'}).`)
  }
  if (form.reportedSymptoms.trim() === '') {
    throw new Error('Please describe the problem.')
  }
  const payload: CasePayload = {
    customerId: form.customerId,
    serviceAddressId: form.serviceAddressId,
    applianceId: form.applianceId,
    reportedSymptoms: form.reportedSymptoms.trim(),
    primarySymptom: form.classification.primarySymptom || null,
    secondarySymptoms: form.classification.secondarySymptoms,
    failureTiming: timingFrom(form.classification),
    relatedCaseId: null,
  }
  const now = new Date().toISOString()
  await database.transaction('rw', database.cases, database.caseErrorCodes, database.outbox, async () => {
    await database.cases.add({
      diagnosticCaseId,
      caseNo: '',
      customerId: payload.customerId,
      serviceAddressId: payload.serviceAddressId,
      applianceId: payload.applianceId,
      // Only for showing the case on this device; the server copies the address itself.
      serviceAddressSnapshot: {
        addressLine1: address.addressLine1,
        addressLine2: address.addressLine2,
        city: address.city,
        province: address.province,
        postalCode: address.postalCode,
        countryCode: address.countryCode,
      },
      reportedSymptoms: payload.reportedSymptoms,
      primarySymptom: payload.primarySymptom,
      secondarySymptoms: payload.secondarySymptoms,
      failureTiming: payload.failureTiming,
      workflowStatus: 'OPEN',
      disposition: null,
      holdReason: '',
      relatedCaseId: null,
      openedAt: now,
      closedAt: null,
      syncState: 'PENDING',
    })
    // The case has to wait for everything it refers to that the server does not have yet.
    const parents = [payload.customerId, payload.serviceAddressId, payload.applianceId]
    const dependsOn: string[] = []
    for (const parentId of parents) {
      const parentCreate = await findCreateMutationId(parentId)
      if (parentCreate) {
        dependsOn.push(parentCreate)
      }
    }
    await enqueueMutation({ entityType: 'CASE', entityId: diagnosticCaseId, operation: 'CREATE', payload, dependsOn })
    // Each code waits behind the case it belongs to.
    for (const code of form.errorCodes) {
      await insertErrorCode(diagnosticCaseId, code)
    }
  })
  return diagnosticCaseId
}

export interface CaseTextForm {
  reportedSymptoms: string
  classification: ClassificationForm
}

/** Only what the customer reported and its classification can be changed; who, where and what cannot. Error codes are changed one by one. */
export async function updateCaseText(caseRecord: LocalCase, form: CaseTextForm): Promise<void> {
  if (isFinished(caseRecord.workflowStatus)) {
    throw new Error('A closed case cannot be changed.')
  }
  if (form.reportedSymptoms.trim() === '') {
    throw new Error('Please describe the problem.')
  }
  const database = db()
  const payload: CasePayload = {
    customerId: caseRecord.customerId,
    serviceAddressId: caseRecord.serviceAddressId,
    applianceId: caseRecord.applianceId,
    reportedSymptoms: form.reportedSymptoms.trim(),
    primarySymptom: form.classification.primarySymptom || null,
    secondarySymptoms: form.classification.secondarySymptoms,
    failureTiming: timingFrom(form.classification),
    relatedCaseId: caseRecord.relatedCaseId,
  }
  await database.transaction('rw', database.cases, database.outbox, async () => {
    await database.cases.update(caseRecord.diagnosticCaseId, {
      reportedSymptoms: payload.reportedSymptoms,
      // The server keeps the old main symptom when none is sent, so keep it here too.
      primarySymptom: payload.primarySymptom ?? caseRecord.primarySymptom,
      secondarySymptoms: payload.secondarySymptoms,
      failureTiming: payload.failureTiming,
      syncState: 'PENDING',
    })
    await enqueueMutation({
      entityType: 'CASE',
      entityId: caseRecord.diagnosticCaseId,
      operation: 'UPDATE',
      rowVersion: caseRecord.rowVersion,
      payload,
    })
  })
}

export interface TransitionInput {
  toState: CaseStatus
  reason: string
  /** Only for a step that closes the case. */
  disposition: CaseDisposition | null
}

/** Moves the case to another state. The step is shown at once and sent later; the server may still refuse it. */
export async function transitionCase(caseRecord: LocalCase, resumeTo: CaseStatus | null, input: TransitionInput): Promise<void> {
  const option = transitionOptions(caseRecord.workflowStatus, resumeTo).find((candidate) => candidate.toState === input.toState)
  if (!option) {
    throw new Error(`A case that is "${statusLabel(caseRecord.workflowStatus)}" cannot move to "${statusLabel(input.toState)}".`)
  }
  // While a visit is going on the case moves only through the visit. This is checked here, and not only by hiding the buttons,
  // because the screen may be out of date and the server refuses such a change as well.
  const visits = await db().visits.where('diagnosticCaseId').equals(caseRecord.diagnosticCaseId).toArray()
  if (visits.some((visit) => visit.visitStatus === 'IN_PROGRESS' || visit.visitStatus === 'PAUSED')) {
    throw new Error('A visit is going on. The case moves on when the visit is completed.')
  }
  const reason = input.reason.trim()
  if (option.needsReason && reason === '') {
    throw new Error('Please say why.')
  }
  if (option.dispositions && (input.disposition === null || !option.dispositions.includes(input.disposition))) {
    throw new Error('Please choose how the case ended.')
  }
  const payload: CaseTransitionPayload = {
    toState: input.toState,
    reason: reason === '' ? null : reason,
    disposition: input.toState === 'CLOSED' ? input.disposition : null,
  }
  const finished = isFinished(input.toState)
  const database = db()
  await database.transaction('rw', database.cases, database.outbox, async () => {
    await database.cases.update(caseRecord.diagnosticCaseId, {
      workflowStatus: input.toState,
      holdReason: input.toState === 'ON_HOLD' ? reason : '',
      // The server sets "cancelled" itself; show the same here.
      disposition: input.toState === 'CLOSED' ? input.disposition : input.toState === 'CANCELLED' ? 'CANCELLED' : null,
      closedAt: finished ? new Date().toISOString() : null,
      syncState: 'PENDING',
    })
    await enqueueMutation({
      entityType: 'CASE',
      entityId: caseRecord.diagnosticCaseId,
      operation: 'TRANSITION',
      rowVersion: caseRecord.rowVersion,
      payload,
    })
  })
}

/** How many cases refer to a customer, an address or an appliance. The server refuses to archive such a record. */
export async function countCasesOf(field: 'customerId' | 'serviceAddressId' | 'applianceId', id: string): Promise<number> {
  return db().cases.where(field).equals(id).count()
}
