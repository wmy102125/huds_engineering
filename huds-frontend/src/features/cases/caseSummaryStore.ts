import { hasPermission, PERMISSION } from '../../api/permissions'
import type {
  CaseSummaryPayload,
  CurrentUser,
  CustomerDecision,
  DiagnosticOutcome,
  FailureCategory,
  RepairStatus,
} from '../../api/types'
import { db } from '../../db/db'
import type { LocalCase, LocalCaseSummary } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'
import { isFinished } from './caseLabels'

/**
 * The summary of a case (sections 6 and 7 of the diagnostic record): what the diagnosis found, how far the repair went,
 * what the customer decided, the subsystems involved and the technician's own words. One record per case.
 * It can be written until the case is closed or cancelled.
 */

export interface SummaryForm {
  diagnosticOutcome: '' | DiagnosticOutcome
  repairStatus: '' | RepairStatus
  customerDecision: '' | CustomerDecision
  failureCategories: FailureCategory[]
  primaryFailureCategory: '' | FailureCategory
  dataTags: string
  technicianSummary: string
}

export function getCaseSummary(diagnosticCaseId: string): Promise<LocalCaseSummary | undefined> {
  return db().caseSummaries.where('diagnosticCaseId').equals(diagnosticCaseId).first()
}

export function summaryFormFrom(record: LocalCaseSummary | undefined): SummaryForm {
  return {
    diagnosticOutcome: record?.diagnosticOutcome ?? '',
    repairStatus: record?.repairStatus ?? '',
    customerDecision: record?.customerDecision ?? '',
    failureCategories: [...(record?.failureCategories ?? [])],
    primaryFailureCategory: record?.primaryFailureCategory ?? '',
    dataTags: record?.dataTags ?? '',
    technicianSummary: record?.technicianSummary ?? '',
  }
}

/** Why the summary cannot be written now, or null when it can. */
export function summaryBlockedReason(caseRecord: LocalCase, user: CurrentUser): string | null {
  if (isFinished(caseRecord.workflowStatus)) {
    return 'The case is closed, so the summary can no longer be changed.'
  }
  if (!hasPermission(user, PERMISSION.CASE_WRITE)) {
    return 'You are not allowed to change the summary of a case.'
  }
  return null
}

/** Saves the summary of a case: a first save creates it, later saves change it. */
export async function saveCaseSummary(
  caseRecord: LocalCase,
  user: CurrentUser,
  record: LocalCaseSummary | undefined,
  form: SummaryForm,
): Promise<void> {
  const blocked = summaryBlockedReason(caseRecord, user)
  if (blocked) {
    throw new Error(blocked)
  }
  const dataTags = form.dataTags.trim()
  const technicianSummary = form.technicianSummary.trim()
  if (dataTags.length > 500) {
    throw new Error('The tags are too long (500 characters at most).')
  }
  if (technicianSummary.length > 5000) {
    throw new Error('The summary is too long (5000 characters at most).')
  }
  if (form.primaryFailureCategory !== '' && !form.failureCategories.includes(form.primaryFailureCategory)) {
    throw new Error('The main failure category must be one of the ticked categories.')
  }
  // The server reads an empty list as "unchanged", so a saved list cannot be cleared.
  if (record && record.failureCategories.length > 0 && form.failureCategories.length === 0) {
    throw new Error('The failure categories cannot be cleared once they are saved. Change them instead.')
  }

  const payload: CaseSummaryPayload = {
    diagnosticCaseId: caseRecord.diagnosticCaseId,
    diagnosticOutcome: form.diagnosticOutcome === '' ? null : form.diagnosticOutcome,
    repairStatus: form.repairStatus === '' ? null : form.repairStatus,
    customerDecision: form.customerDecision === '' ? null : form.customerDecision,
    failureCategories: form.failureCategories,
    primaryFailureCategory: form.primaryFailureCategory === '' ? null : form.primaryFailureCategory,
    dataTags,
    technicianSummary,
  }
  const { diagnosticCaseId, ...values } = payload
  const database = db()
  await database.transaction('rw', database.caseSummaries, database.outbox, async () => {
    const existing = await database.caseSummaries.where('diagnosticCaseId').equals(diagnosticCaseId).first()
    if (existing) {
      await database.caseSummaries.update(existing.caseSummaryRecordId, { ...values, syncState: 'PENDING' })
      await enqueueMutation({
        entityType: 'CASE_SUMMARY',
        entityId: existing.caseSummaryRecordId,
        operation: 'UPDATE',
        rowVersion: existing.rowVersion,
        payload,
      })
      return
    }
    const caseSummaryRecordId = crypto.randomUUID()
    await database.caseSummaries.add({ caseSummaryRecordId, diagnosticCaseId, ...values, syncState: 'PENDING' })
    // It waits behind the case when the server does not have the case yet.
    const caseCreate = await findCreateMutationId(diagnosticCaseId)
    await enqueueMutation({
      entityType: 'CASE_SUMMARY',
      entityId: caseSummaryRecordId,
      operation: 'CREATE',
      payload,
      dependsOn: caseCreate ? [caseCreate] : [],
    })
  })
}
