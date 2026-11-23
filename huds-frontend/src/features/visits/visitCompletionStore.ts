import type {
  CurrentUser,
  FinalStatus,
  FixOutcome,
  GoogleReviewStatus,
  VerificationChecks,
  VisitCompletionPayload,
} from '../../api/types'
import { db } from '../../db/db'
import type { LocalVisit, LocalVisitCompletion } from '../../db/db'
import { enqueueMutation, findLastUnsentMutationId } from '../../sync/outbox'
import { visitRecordsBlockedReason } from './visitRecordGuard'

/**
 * The completion record of a visit (section 5 of the diagnostic form): verification checks, the final status of the
 * appliance, the fix outcome and the Google review status. One record per visit, with an id of its own.
 * It is filled in on the visit page while the visit is in progress or paused (before the visit is completed).
 */

export interface CompletionForm {
  verificationChecks: VerificationChecks
  finalStatus: '' | FinalStatus
  fixOutcome: '' | FixOutcome
  googleReviewStatus: '' | GoogleReviewStatus
}

export function getVisitCompletion(serviceVisitId: string): Promise<LocalVisitCompletion | undefined> {
  return db().visitCompletions.where('serviceVisitId').equals(serviceVisitId).first()
}

export function completionFormFrom(record: LocalVisitCompletion | undefined): CompletionForm {
  return {
    verificationChecks: { ...(record?.verificationChecks ?? {}) },
    finalStatus: record?.finalStatus ?? '',
    fixOutcome: record?.fixOutcome ?? '',
    googleReviewStatus: record?.googleReviewStatus ?? '',
  }
}

/** Saves the completion record of a visit: a first save creates it, later saves change it. */
export async function saveVisitCompletion(visit: LocalVisit, user: CurrentUser, form: CompletionForm): Promise<void> {
  const blocked = visitRecordsBlockedReason(visit, user)
  if (blocked) {
    throw new Error(blocked)
  }
  // Only the ticked checks are stored; a check that is not there was not done or not recorded.
  const verificationChecks: VerificationChecks = {}
  for (const [code, ticked] of Object.entries(form.verificationChecks)) {
    if (ticked) {
      verificationChecks[code as keyof VerificationChecks] = true
    }
  }
  const values = {
    verificationChecks,
    finalStatus: form.finalStatus === '' ? null : form.finalStatus,
    fixOutcome: form.fixOutcome === '' ? null : form.fixOutcome,
    googleReviewStatus: form.googleReviewStatus === '' ? null : form.googleReviewStatus,
  }
  const payload: VisitCompletionPayload = { serviceVisitId: visit.serviceVisitId, ...values }
  const database = db()
  await database.transaction('rw', database.visitCompletions, database.outbox, async () => {
    const existing = await database.visitCompletions.where('serviceVisitId').equals(visit.serviceVisitId).first()
    if (existing) {
      await database.visitCompletions.update(existing.visitCompletionRecordId, { ...values, syncState: 'PENDING' })
      await enqueueMutation({
        entityType: 'VISIT_COMPLETION',
        entityId: existing.visitCompletionRecordId,
        operation: 'UPDATE',
        rowVersion: existing.rowVersion,
        payload,
      })
      return
    }
    const visitCompletionRecordId = crypto.randomUUID()
    await database.visitCompletions.add({ visitCompletionRecordId, ...payload, syncState: 'PENDING' })
    // The server accepts the record only while the visit is on site, so it must come after the start of the visit.
    const lastVisitChange = await findLastUnsentMutationId('SERVICE_VISIT', visit.serviceVisitId)
    await enqueueMutation({
      entityType: 'VISIT_COMPLETION',
      entityId: visitCompletionRecordId,
      operation: 'CREATE',
      payload,
      dependsOn: lastVisitChange ? [lastVisitChange] : [],
    })
  })
}
