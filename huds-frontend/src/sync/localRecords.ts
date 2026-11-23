import type { SyncEntityType, SyncOperation } from '../api/types'
import { db } from '../db/db'
import type { SyncState } from '../db/db'

/**
 * The sync engine does not know what a customer or an appliance is. This file is the only place
 * that maps an entity type to its local table. Add a case here for every new entity type.
 */

/** Marks the local copy of a record (for example as refused by the server). */
export async function markSyncState(entityType: SyncEntityType, entityId: string, state: SyncState): Promise<void> {
  switch (entityType) {
    case 'CUSTOMER':
      await db().customers.update(entityId, { syncState: state })
      break
    case 'SERVICE_ADDRESS':
      await db().addresses.update(entityId, { syncState: state })
      break
    case 'APPLIANCE_MODEL':
      await db().models.update(entityId, { syncState: state })
      break
    case 'APPLIANCE':
      await db().appliances.update(entityId, { syncState: state })
      break
    case 'CASE':
      await db().cases.update(entityId, { syncState: state })
      break
    case 'CASE_ERROR_CODE':
      await db().caseErrorCodes.update(entityId, { syncState: state })
      break
    case 'CASE_PART':
      await db().caseParts.update(entityId, { syncState: state })
      break
    case 'SERVICE_VISIT':
      await db().visits.update(entityId, { syncState: state })
      break
    case 'VISIT_SAFETY_CHECK':
      await db().visitSafetyChecks.update(entityId, { syncState: state })
      break
    case 'DIAGNOSTIC_STEP':
      await db().diagnosticSteps.update(entityId, { syncState: state })
      break
    case 'VISIT_COMPLETION':
      await db().visitCompletions.update(entityId, { syncState: state })
      break
    case 'CASE_SUMMARY':
      await db().caseSummaries.update(entityId, { syncState: state })
      break
    case 'CASE_INVOICE':
      await db().caseInvoices.update(entityId, { syncState: state })
      break
    case 'CASE_INVOICE_PAYMENT':
      await db().caseInvoicePayments.update(entityId, { syncState: state })
      break
    case 'ATTACHMENT':
      await db().attachments.update(entityId, { syncState: state })
      break
    case 'APPLIANCE_DOCUMENT':
      await db().applianceDocuments.update(entityId, { syncState: state })
      break
  }
}

/**
 * The user gave up a change that the server refused, and the record exists on the server:
 * it is no longer waiting to be archived and is marked as in step with the server again.
 */
export async function clearLocalChange(entityType: SyncEntityType, entityId: string): Promise<void> {
  const changes = { syncState: 'SYNCED' as const, archiving: false }
  switch (entityType) {
    case 'CUSTOMER':
      await db().customers.update(entityId, changes)
      break
    case 'SERVICE_ADDRESS':
      await db().addresses.update(entityId, changes)
      break
    case 'APPLIANCE_MODEL':
      await db().models.update(entityId, changes)
      break
    case 'APPLIANCE':
      await db().appliances.update(entityId, changes)
      break
    case 'CASE':
      await db().cases.update(entityId, changes)
      break
    case 'CASE_ERROR_CODE':
      await db().caseErrorCodes.update(entityId, changes)
      break
    case 'CASE_PART':
      await db().caseParts.update(entityId, changes)
      break
    case 'SERVICE_VISIT':
      await db().visits.update(entityId, changes)
      break
    case 'VISIT_SAFETY_CHECK':
      await db().visitSafetyChecks.update(entityId, changes)
      break
    case 'DIAGNOSTIC_STEP':
      await db().diagnosticSteps.update(entityId, changes)
      break
    case 'VISIT_COMPLETION':
      await db().visitCompletions.update(entityId, changes)
      break
    case 'CASE_SUMMARY':
      await db().caseSummaries.update(entityId, changes)
      break
    case 'CASE_INVOICE':
      await db().caseInvoices.update(entityId, changes)
      break
    case 'CASE_INVOICE_PAYMENT':
      await db().caseInvoicePayments.update(entityId, changes)
      break
    case 'ATTACHMENT':
      await db().attachments.update(entityId, changes)
      break
    case 'APPLIANCE_DOCUMENT':
      await db().applianceDocuments.update(entityId, changes)
      break
  }
}

/** The server accepted a change: store the new row version, or remove the record if it was archived. */
export async function applyAcceptedLocal(
  entityType: SyncEntityType,
  entityId: string,
  operation: SyncOperation,
  rowVersion: number | undefined,
  stillWaiting: boolean,
): Promise<void> {
  const syncState: SyncState = stillWaiting ? 'PENDING' : 'SYNCED'
  if (operation === 'ARCHIVE') {
    await deleteLocal(entityType, entityId)
    return
  }
  switch (entityType) {
    case 'CUSTOMER':
      await db().customers.update(entityId, { rowVersion, syncState })
      break
    case 'SERVICE_ADDRESS':
      await db().addresses.update(entityId, { rowVersion, syncState })
      break
    case 'APPLIANCE_MODEL':
      await db().models.update(entityId, { rowVersion, syncState })
      break
    case 'APPLIANCE':
      await db().appliances.update(entityId, { rowVersion, syncState })
      break
    case 'CASE':
      await db().cases.update(entityId, { rowVersion, syncState })
      break
    case 'CASE_ERROR_CODE':
      await db().caseErrorCodes.update(entityId, { rowVersion, syncState })
      break
    case 'CASE_PART':
      await db().caseParts.update(entityId, { rowVersion, syncState })
      break
    case 'SERVICE_VISIT':
      await db().visits.update(entityId, { rowVersion, syncState })
      break
    case 'VISIT_SAFETY_CHECK':
      await db().visitSafetyChecks.update(entityId, { rowVersion, syncState })
      break
    case 'DIAGNOSTIC_STEP':
      await db().diagnosticSteps.update(entityId, { rowVersion, syncState })
      break
    case 'VISIT_COMPLETION':
      await db().visitCompletions.update(entityId, { rowVersion, syncState })
      break
    case 'CASE_SUMMARY':
      await db().caseSummaries.update(entityId, { rowVersion, syncState })
      break
    case 'CASE_INVOICE':
      await db().caseInvoices.update(entityId, { rowVersion, syncState })
      break
    case 'CASE_INVOICE_PAYMENT':
      await db().caseInvoicePayments.update(entityId, { rowVersion, syncState })
      break
    case 'ATTACHMENT':
      await db().attachments.update(entityId, { rowVersion, syncState })
      break
    case 'APPLIANCE_DOCUMENT':
      await db().applianceDocuments.update(entityId, { rowVersion, syncState })
      break
  }
}

/** Removes a record from this device (its CREATE was given up, or the server archived it). */
export async function deleteLocal(entityType: SyncEntityType, entityId: string): Promise<void> {
  switch (entityType) {
    case 'CUSTOMER':
      await db().customers.delete(entityId)
      break
    case 'SERVICE_ADDRESS':
      await db().addresses.delete(entityId)
      break
    case 'APPLIANCE_MODEL':
      await db().models.delete(entityId)
      break
    case 'APPLIANCE':
      await db().appliances.delete(entityId)
      break
    case 'CASE':
      await db().cases.delete(entityId)
      break
    case 'CASE_ERROR_CODE':
      await db().caseErrorCodes.delete(entityId)
      break
    case 'CASE_PART':
      await db().caseParts.delete(entityId)
      break
    case 'SERVICE_VISIT':
      await db().visits.delete(entityId)
      break
    case 'VISIT_SAFETY_CHECK':
      await db().visitSafetyChecks.delete(entityId)
      break
    case 'DIAGNOSTIC_STEP':
      await db().diagnosticSteps.delete(entityId)
      break
    case 'VISIT_COMPLETION':
      await db().visitCompletions.delete(entityId)
      break
    case 'CASE_SUMMARY':
      await db().caseSummaries.delete(entityId)
      break
    case 'CASE_INVOICE':
      await db().caseInvoices.delete(entityId)
      break
    case 'CASE_INVOICE_PAYMENT':
      await db().caseInvoicePayments.delete(entityId)
      break
    case 'ATTACHMENT':
      await db().attachments.delete(entityId)
      // The file that waited for its upload is of no use any more.
      await db().attachmentFiles.delete(entityId)
      break
    case 'APPLIANCE_DOCUMENT':
      await db().applianceDocuments.delete(entityId)
      // The file that waited for its upload is of no use any more.
      await db().applianceDocumentFiles.delete(entityId)
      break
  }
}
