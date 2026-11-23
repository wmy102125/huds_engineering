import { db } from '../db/db'
import type { OutboxItem } from '../db/db'
import { clearLocalChange, deleteLocal } from './localRecords'
import { outboxOfRecord } from './outbox'
import { resetCursor } from './pull'

/** Changes the server refused. The user has to decide what to do with them. */
export function listRejected(): Promise<OutboxItem[]> {
  return db().outbox.where('status').equals('REJECTED').sortBy('seq')
}

/**
 * The user gives up a change that the server refused. The change leaves the outbox.
 * A record that never existed on the server disappears (with the changes that waited for it).
 * Any other record goes back to what the server has: the download starts from the beginning once,
 * because the server only sends records that changed, and this one did not.
 */
export async function discardRejected(item: OutboxItem): Promise<void> {
  const database = db()
  const tables = [
    database.customers,
    database.addresses,
    database.models,
    database.appliances,
    database.cases,
    database.caseErrorCodes,
    database.caseParts,
    database.visits,
    database.visitSafetyChecks,
    database.diagnosticSteps,
    database.visitCompletions,
    database.caseSummaries,
    database.caseInvoices,
    database.caseInvoicePayments,
    database.attachments,
    database.attachmentFiles,
    database.applianceDocuments,
    database.applianceDocumentFiles,
    database.outbox,
  ]
  await database.transaction('rw', tables, async () => {
    await database.outbox.delete(item.seq)
    if (item.entityType === 'CASE' || item.entityType === 'SERVICE_VISIT') {
      // The steps of a case or a visit were made one on top of the other; the later ones cannot succeed without this one.
      const sameRecord = await outboxOfRecord(item.entityType, item.entityId)
      for (const later of sameRecord) {
        if (later.seq > item.seq && later.status !== 'REJECTED') {
          await database.outbox.delete(later.seq)
        }
      }
    }
    // A REGISTER creates the record on the server just like a CREATE does.
    if (item.operation === 'CREATE' || item.operation === 'REGISTER') {
      await deleteLocal(item.entityType, item.entityId)
      // Changes that waited for this record (for example the addresses of a new customer) cannot succeed any more.
      // The same goes for what waited for them in turn (a case waits for the appliance, the appliance for its model).
      const lostMutationIds = [item.mutationId]
      while (lostMutationIds.length > 0) {
        const lostId = lostMutationIds.pop()
        const dependants = await database.outbox.filter((other) => other.dependsOn.includes(lostId ?? '')).toArray()
        for (const other of dependants) {
          // Everything that waited to be sent for the dependent record goes too (a case can have several steps queued).
          const queued = await outboxOfRecord(other.entityType, other.entityId)
          for (const queuedItem of queued) {
            await database.outbox.delete(queuedItem.seq)
            if (queuedItem.operation === 'CREATE') {
              lostMutationIds.push(queuedItem.mutationId)
            }
          }
          await deleteLocal(other.entityType, other.entityId)
        }
      }
    } else {
      // What waited for this change cannot succeed any more (the safety checks of a visit wait for its start).
      const dependants = await database.outbox.filter((other) => other.dependsOn.includes(item.mutationId)).toArray()
      for (const other of dependants) {
        await database.outbox.delete(other.seq)
        if (other.operation === 'CREATE' || other.operation === 'REGISTER') {
          await deleteLocal(other.entityType, other.entityId)
        } else {
          await clearLocalChange(other.entityType, other.entityId)
        }
      }
      await clearLocalChange(item.entityType, item.entityId)
    }
  })
  if (item.operation !== 'CREATE' && item.operation !== 'REGISTER') {
    await resetCursor(item.entityType)
  }
}

/** A short text that tells the user which record a change was about. */
export function describeChange(item: OutboxItem): string {
  const payload = item.payload as
    | {
        customerName?: string
        addressLine1?: string
        brand?: string
        modelNo?: string
        serialNo?: string
        fileName?: string
        documentRef?: string
        partName?: string
        reportedSymptoms?: string
        toState?: string
      }
    | undefined
  const what: Record<string, string> = {
    CUSTOMER: 'Customer',
    SERVICE_ADDRESS: 'Address',
    APPLIANCE_MODEL: 'Appliance model',
    APPLIANCE: 'Appliance',
    CASE: 'Case',
    SERVICE_VISIT: 'Visit',
    VISIT_SAFETY_CHECK: 'Safety checks',
    DIAGNOSTIC_STEP: 'Diagnostic step',
    VISIT_COMPLETION: 'Completion record',
    CASE_SUMMARY: 'Case summary',
    CASE_INVOICE: 'Bill',
    CASE_INVOICE_PAYMENT: 'Payment',
    ATTACHMENT: 'File',
    APPLIANCE_DOCUMENT: 'Document',
    CASE_PART: 'Part',
  }
  const label = what[item.entityType] ?? item.entityType
  const brandAndModel = [payload?.brand, payload?.modelNo].filter(Boolean).join(' ')
  if (item.entityType === 'CASE' && item.operation === 'TRANSITION') {
    return `Case step to ${payload?.toState ?? '?'}`
  }
  if (item.entityType === 'SERVICE_VISIT' && item.operation === 'TRANSITION') {
    return `Visit step to ${payload?.toState ?? '?'}`
  }
  const name = payload?.customerName || payload?.addressLine1 || brandAndModel || payload?.serialNo || payload?.reportedSymptoms || payload?.fileName || payload?.documentRef || payload?.partName
  return name ? `${label} "${name}"` : `${label} (${item.operation.toLowerCase()})`
}
