import { hasPermission, PERMISSION } from '../../api/permissions'
import type {
  CurrentUser,
  EnvironmentBaseline,
  SafetyChecks,
  UtilityBaseline,
  VisitSafetyCheckPayload,
} from '../../api/types'
import { db } from '../../db/db'
import type { LocalVisit, LocalVisitSafetyCheck } from '../../db/db'
import { enqueueMutation, findLastUnsentMutationId } from '../../sync/outbox'

/**
 * The safety and baseline checks of a visit (section 2 of the diagnostic form).
 * One record per visit; its id is the id of the visit. It is filled in on the visit page while the visit is in progress or paused,
 * by the technician of the visit (or an administrator). The visit cannot be completed before the record exists.
 */

/** The values of the form as the user types them. Numbers are text until the form is saved. */
export interface SafetyCheckForm {
  safetyChecks: SafetyChecks
  supplyType: '' | 'V120_1PH' | 'V120_240_SPLIT'
  l1nV: string
  l2nV: string
  l1l2V: string
  waterTempF: string
  pressureStaticPsi: string
  pressureDynamicPsi: string
  gas: '' | 'NG' | 'LP'
  ambientNotes: string
}

export function getSafetyCheck(serviceVisitId: string): Promise<LocalVisitSafetyCheck | undefined> {
  return db().visitSafetyChecks.get(serviceVisitId)
}

/** The visit is on site (in progress or paused): the checks can be written now. */
export function isVisitOnSite(visit: LocalVisit): boolean {
  return visit.visitStatus === 'IN_PROGRESS' || visit.visitStatus === 'PAUSED'
}

/** Why the checks cannot be written now, or null when they can. */
export function safetyCheckBlockedReason(visit: LocalVisit, user: CurrentUser): string | null {
  if (!isVisitOnSite(visit)) {
    return visit.visitStatus === 'PLANNED'
      ? 'Start the visit first.'
      : 'The visit is finished, so the checks can no longer be changed.'
  }
  if (visit.technicianId !== user.userId && !hasPermission(user, PERMISSION.VISIT_ASSIGN)) {
    return 'This visit is assigned to another technician.'
  }
  return null
}

export function formFrom(record: LocalVisitSafetyCheck | undefined): SafetyCheckForm {
  const text = (value: number | undefined) => (value === undefined ? '' : String(value))
  return {
    safetyChecks: { ...(record?.safetyChecks ?? {}) },
    supplyType: record?.utilityBaseline.supplyType ?? '',
    l1nV: text(record?.utilityBaseline.l1nV),
    l2nV: text(record?.utilityBaseline.l2nV),
    l1l2V: text(record?.utilityBaseline.l1l2V),
    waterTempF: text(record?.environmentBaseline.waterTempF),
    pressureStaticPsi: text(record?.environmentBaseline.pressureStaticPsi),
    pressureDynamicPsi: text(record?.environmentBaseline.pressureDynamicPsi),
    gas: record?.environmentBaseline.gas ?? '',
    ambientNotes: record?.environmentBaseline.ambientNotes ?? '',
  }
}

/** An empty field is left out; a number that is not a number is refused with a message that names the field. */
function readNumber(text: string, label: string): number | undefined {
  const trimmed = text.trim()
  if (trimmed === '') {
    return undefined
  }
  const value = Number(trimmed)
  if (!Number.isFinite(value) || value < 0) {
    throw new Error(`${label} must be a number, 0 or more.`)
  }
  return value
}

/** Builds what is sent to the server. Only what was filled in is part of it. */
export function payloadFrom(form: SafetyCheckForm): VisitSafetyCheckPayload {
  const utilityBaseline: UtilityBaseline = {}
  if (form.supplyType !== '') {
    utilityBaseline.supplyType = form.supplyType
  }
  for (const [key, label] of [
    ['l1nV', 'L1-N voltage'],
    ['l2nV', 'L2-N voltage'],
    ['l1l2V', 'L1-L2 voltage'],
  ] as const) {
    const value = readNumber(form[key], label)
    if (value !== undefined) {
      utilityBaseline[key] = value
    }
  }

  const environmentBaseline: EnvironmentBaseline = {}
  for (const [key, label] of [
    ['waterTempF', 'Water temperature'],
    ['pressureStaticPsi', 'Static pressure'],
    ['pressureDynamicPsi', 'Dynamic pressure'],
  ] as const) {
    const value = readNumber(form[key], label)
    if (value !== undefined) {
      environmentBaseline[key] = value
    }
  }
  if (form.gas !== '') {
    environmentBaseline.gas = form.gas
  }
  if (form.ambientNotes.trim() !== '') {
    environmentBaseline.ambientNotes = form.ambientNotes.trim()
  }

  // Only the ticked checks are stored; a check that is not there was not done or not recorded.
  const safetyChecks: SafetyChecks = {}
  for (const [code, ticked] of Object.entries(form.safetyChecks)) {
    if (ticked) {
      safetyChecks[code as keyof SafetyChecks] = true
    }
  }
  return { safetyChecks, utilityBaseline, environmentBaseline }
}

/** Saves the checks of a visit: a first save creates the record, later saves change it. */
export async function saveSafetyCheck(visit: LocalVisit, user: CurrentUser, form: SafetyCheckForm): Promise<void> {
  const blocked = safetyCheckBlockedReason(visit, user)
  if (blocked) {
    throw new Error(blocked)
  }
  const payload = payloadFrom(form)
  const database = db()
  await database.transaction('rw', database.visitSafetyChecks, database.outbox, async () => {
    const existing = await database.visitSafetyChecks.get(visit.serviceVisitId)
    if (existing) {
      await database.visitSafetyChecks.update(visit.serviceVisitId, { ...payload, syncState: 'PENDING' })
      await enqueueMutation({
        entityType: 'VISIT_SAFETY_CHECK',
        entityId: visit.serviceVisitId,
        operation: 'UPDATE',
        rowVersion: existing.rowVersion,
        payload,
      })
      return
    }
    await database.visitSafetyChecks.add({ serviceVisitId: visit.serviceVisitId, ...payload, syncState: 'PENDING' })
    // The server accepts the checks only while the visit is on site, so they must come after the start of the visit
    // (and after the creation of the visit, when that has not been sent yet).
    const lastVisitChange = await findLastUnsentMutationId('SERVICE_VISIT', visit.serviceVisitId)
    await enqueueMutation({
      entityType: 'VISIT_SAFETY_CHECK',
      entityId: visit.serviceVisitId,
      operation: 'CREATE',
      payload,
      dependsOn: lastVisitChange ? [lastVisitChange] : [],
    })
  })
}
