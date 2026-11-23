import { apiRequest } from '../api/http'
import type {
  ApplianceDocumentChange,
  CasePartChange,
  AttachmentChange,
  UserChange,
  VisitChange,
  ApplianceChange,
  ApplianceModelChange,
  CaseChange,
  CaseErrorCodeChange,
  CustomerChange,
  PullEntityType,
  PullResponse,
  VisitSafetyCheckChange,
  DiagnosticStepChange,
  VisitCompletionChange,
  CaseSummaryChange,
  CaseInvoiceChange,
  CaseInvoicePaymentChange,
  ServiceAddressChange,
  StateEventChange,
  SyncEntityType,
} from '../api/types'
import { db } from '../db/db'
import type { AuditFields } from '../db/db'

/**
 * Downloads what changed on the server since the last download, one entity type after the other.
 * The server returns changes oldest first, in batches. A record that was archived comes with archived=true
 * and is removed from this device.
 */

// Parents before children: a case refers to the customer, address and appliance, and the events refer to the case.
const PULL_TYPES: PullEntityType[] = [
  'CUSTOMER',
  'SERVICE_ADDRESS',
  // Before the models: a model refers to its schematic and its service manual.
  'APPLIANCE_DOCUMENT',
  'APPLIANCE_MODEL',
  'APPLIANCE',
  'CASE',
  'CASE_ERROR_CODE',
  // After the cases: a part belongs to a case.
  'CASE_PART',
  'STATE_EVENT',
  'USER',
  'SERVICE_VISIT',
  // After the visits: a record of this type is the section 2 of one visit.
  'VISIT_SAFETY_CHECK',
  'DIAGNOSTIC_STEP',
  'VISIT_COMPLETION',
  // After the cases. The bill comes before its payments.
  'CASE_SUMMARY',
  'CASE_INVOICE',
  'CASE_INVOICE_PAYMENT',
  // Last: a file belongs to a case, an appliance or a visit, which are all downloaded before.
  'ATTACHMENT',
]

/**
 * The visit types of M3. While the backend of M3 is not deployed the server does not know them and refuses the download;
 * that must not stop the download of everything else. Remove this list once the backend of M3 is running,
 * so that a real error is no longer hidden.
 */
const OPTIONAL_TYPES: ReadonlySet<PullEntityType> = new Set<PullEntityType>([
  'USER',
  'SERVICE_VISIT',
  'VISIT_SAFETY_CHECK',
  'DIAGNOSTIC_STEP',
  'VISIT_COMPLETION',
  'CASE_SUMMARY',
  'CASE_INVOICE',
  'CASE_INVOICE_PAYMENT',
])

/** Every download starts a little before the stored cursor, so a row that was committed late is not missed. */
const OVERLAP_MS = 5_000
const MAX_BATCHES = 200
const FIRST_ID = '00000000-0000-0000-0000-000000000000'

function cursorKey(entityType: PullEntityType): string {
  return `cursor:${entityType}`
}

export async function pullAll(): Promise<void> {
  for (const entityType of PULL_TYPES) {
    if (OPTIONAL_TYPES.has(entityType)) {
      try {
        await pullType(entityType)
      } catch (error) {
        console.warn(`Download of ${entityType} skipped:`, error)
      }
    } else {
      await pullType(entityType)
    }
  }
}

/** Forgets where the download stopped, so the next download starts from the beginning and brings the server's version of every record. */
export async function resetCursor(entityType: SyncEntityType): Promise<void> {
  await db().meta.delete(cursorKey(entityType))
}

async function pullType(entityType: PullEntityType): Promise<void> {
  const database = db()
  const stored = await database.meta.get(cursorKey(entityType))

  let since: string | undefined
  let sinceId: string | undefined
  if (stored) {
    // Applying the same record twice does no harm.
    since = new Date(Date.parse(stored.since) - OVERLAP_MS).toISOString()
    sinceId = FIRST_ID
  }

  for (let batch = 0; batch < MAX_BATCHES; batch++) {
    const query = new URLSearchParams({ entityType })
    if (since !== undefined && sinceId !== undefined) {
      query.set('since', since)
      query.set('sinceId', sinceId)
    }
    const response = await apiRequest<PullResponse<unknown>>(`/api/v1/sync/pull?${query.toString()}`)
    await applyBatch(entityType, response.items)

    // Without records the server gives our own (earlier) cursor back. Keeping it would move the cursor backwards every time.
    if (response.items.length > 0) {
      await database.meta.put({ key: cursorKey(entityType), since: response.nextSince, sinceId: response.nextSinceId })
    }
    if (!response.hasMore) {
      return
    }
    since = response.nextSince
    sinceId = response.nextSinceId
  }
}

async function applyBatch(entityType: PullEntityType, items: unknown[]): Promise<void> {
  switch (entityType) {
    case 'CUSTOMER':
      await applyCustomers(items as CustomerChange[])
      break
    case 'SERVICE_ADDRESS':
      await applyAddresses(items as ServiceAddressChange[])
      break
    case 'APPLIANCE_DOCUMENT':
      await applyApplianceDocuments(items as ApplianceDocumentChange[])
      break
    case 'APPLIANCE_MODEL':
      await applyModels(items as ApplianceModelChange[])
      break
    case 'APPLIANCE':
      await applyAppliances(items as ApplianceChange[])
      break
    case 'CASE':
      await applyCases(items as CaseChange[])
      break
    case 'CASE_ERROR_CODE':
      await applyCaseErrorCodes(items as CaseErrorCodeChange[])
      break
    case 'CASE_PART':
      await applyCaseParts(items as CasePartChange[])
      break
    case 'STATE_EVENT':
      await applyStateEvents(items as StateEventChange[])
      break
    case 'USER':
      await applyUsers(items as UserChange[])
      break
    case 'SERVICE_VISIT':
      await applyVisits(items as VisitChange[])
      break
    case 'VISIT_SAFETY_CHECK':
      await applyVisitSafetyChecks(items as VisitSafetyCheckChange[])
      break
    case 'DIAGNOSTIC_STEP':
      await applyDiagnosticSteps(items as DiagnosticStepChange[])
      break
    case 'VISIT_COMPLETION':
      await applyVisitCompletions(items as VisitCompletionChange[])
      break
    case 'CASE_SUMMARY':
      await applyCaseSummaries(items as CaseSummaryChange[])
      break
    case 'CASE_INVOICE':
      await applyCaseInvoices(items as CaseInvoiceChange[])
      break
    case 'CASE_INVOICE_PAYMENT':
      await applyCaseInvoicePayments(items as CaseInvoicePaymentChange[])
      break
    case 'ATTACHMENT':
      await applyAttachments(items as AttachmentChange[])
      break
  }
}

type AuditedChange =
  | ApplianceChange
  | ApplianceModelChange
  | CaseChange
  | CaseErrorCodeChange
  | CasePartChange
  | CustomerChange
  | ServiceAddressChange
  | VisitChange
  | VisitSafetyCheckChange
  | DiagnosticStepChange
  | VisitCompletionChange
  | CaseSummaryChange
  | CaseInvoiceChange
  | CaseInvoicePaymentChange

function auditOf(item: AuditedChange): AuditFields {
  return {
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
    createdByName: item.createdBy?.userName,
    updatedByName: item.updatedBy?.userName,
  }
}

// In every function below, a record with changes of its own that the server does not know yet is left alone.

async function applyCustomers(items: CustomerChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.customers, async () => {
    for (const item of items) {
      const existing = await database.customers.get(item.customerId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      if (item.archived) {
        await database.customers.delete(item.customerId)
        continue
      }
      await database.customers.put({
        customerId: item.customerId,
        customerName: item.customerName,
        customerType: item.customerType,
        email: item.email ?? '',
        phone: item.phone ?? '',
        notes: item.notes ?? '',
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

async function applyAddresses(items: ServiceAddressChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.addresses, async () => {
    for (const item of items) {
      const existing = await database.addresses.get(item.serviceAddressId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      if (item.archived) {
        await database.addresses.delete(item.serviceAddressId)
        continue
      }
      await database.addresses.put({
        serviceAddressId: item.serviceAddressId,
        customerId: item.customerId,
        addressLine1: item.addressLine1,
        addressLine2: item.addressLine2 ?? '',
        city: item.city,
        province: item.province,
        postalCode: item.postalCode ?? '',
        countryCode: item.countryCode,
        isDefault: item.isDefault,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

async function applyModels(items: ApplianceModelChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.models, async () => {
    for (const item of items) {
      const existing = await database.models.get(item.applianceModelId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      if (item.archived) {
        await database.models.delete(item.applianceModelId)
        continue
      }
      await database.models.put({
        applianceModelId: item.applianceModelId,
        applianceType: item.applianceType,
        brand: item.brand ?? '',
        modelNo: item.modelNo ?? '',
        schematicDocumentId: item.schematicDocumentId,
        schematicLocation: item.schematicLocation ?? '',
        serviceManualDocumentId: item.serviceManualDocumentId,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

async function applyAppliances(items: ApplianceChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.appliances, async () => {
    for (const item of items) {
      const existing = await database.appliances.get(item.applianceId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      if (item.archived) {
        await database.appliances.delete(item.applianceId)
        continue
      }
      await database.appliances.put({
        applianceId: item.applianceId,
        customerId: item.customerId,
        applianceModelId: item.applianceModelId,
        serialNo: item.serialNo ?? '',
        serialVerified: item.serialVerified,
        identityNotes: item.identityNotes ?? '',
        customerAssetRef: item.customerAssetRef ?? '',
        purchaseOrderNumber: item.purchaseOrderNumber ?? '',
        productionDate: item.productionDate,
        estimatedAgeYears: item.estimatedAgeYears,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** A case is never archived, so no case is ever removed here. */
async function applyCases(items: CaseChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.cases, async () => {
    for (const item of items) {
      const existing = await database.cases.get(item.diagnosticCaseId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.cases.put({
        diagnosticCaseId: item.diagnosticCaseId,
        caseNo: item.caseNo,
        customerId: item.customerId,
        serviceAddressId: item.serviceAddressId,
        applianceId: item.applianceId,
        serviceAddressSnapshot: item.serviceAddressSnapshot ?? {},
        reportedSymptoms: item.reportedSymptoms,
        primarySymptom: item.primarySymptom,
        secondarySymptoms: item.secondarySymptoms ?? [],
        failureTiming: item.failureTiming ?? {},
        workflowStatus: item.workflowStatus,
        disposition: item.disposition,
        holdReason: item.holdReason ?? '',
        relatedCaseId: item.relatedCaseId,
        openedAt: item.openedAt,
        closedAt: item.closedAt,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** An error code is never deleted (it is set to STORED_HISTORY), so no row is ever removed here. */
async function applyCaseErrorCodes(items: CaseErrorCodeChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.caseErrorCodes, async () => {
    for (const item of items) {
      const existing = await database.caseErrorCodes.get(item.caseErrorCodeId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.caseErrorCodes.put({
        caseErrorCodeId: item.caseErrorCodeId,
        diagnosticCaseId: item.diagnosticCaseId,
        errorCode: item.errorCode,
        displayMessage: item.displayMessage ?? '',
        codeStatus: item.codeStatus,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** A part that was archived on the server (entered by mistake) is removed from this device. */
async function applyCaseParts(items: CasePartChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.caseParts, async () => {
    for (const item of items) {
      const existing = await database.caseParts.get(item.casePartId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      if (item.archived) {
        await database.caseParts.delete(item.casePartId)
        continue
      }
      await database.caseParts.put({
        casePartId: item.casePartId,
        diagnosticCaseId: item.diagnosticCaseId,
        partName: item.partName,
        oemPartNo: item.oemPartNo ?? '',
        quantity: item.quantity,
        unit: item.unit ?? '',
        supplier: item.supplier ?? '',
        partsSupplyStatus: item.partsSupplyStatus,
        orderedOn: item.orderedOn,
        expectedArrivalOn: item.expectedArrivalOn,
        arrivedOn: item.arrivedOn,
        warrantyMonths: item.warrantyMonths,
        installedVisitId: item.installedVisitId,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** The events are written by the server and never change, so the local copy is simply replaced. */
async function applyStateEvents(items: StateEventChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.stateEvents, async () => {
    for (const item of items) {
      await database.stateEvents.put({
        stateEventId: item.stateEventId,
        entityType: item.entityType,
        entityId: item.entityId,
        fromState: item.fromState,
        toState: item.toState,
        reason: item.reason ?? '',
        actor: item.actor,
        occurredAt: item.occurredAt,
        clientRecordedAt: item.clientRecordedAt,
      })
    }
  })
}

/** The list of users is small and read only: the local copy is simply replaced. A deactivated user is removed. */
async function applyUsers(items: UserChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.users, async () => {
    for (const item of items) {
      if (item.active) {
        await database.users.put({ userId: item.userId, userName: item.userName, role: item.role })
      } else {
        await database.users.delete(item.userId)
      }
    }
  })
}

/** A visit is never archived, so no visit is ever removed here. */
async function applyVisits(items: VisitChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.visits, async () => {
    for (const item of items) {
      const existing = await database.visits.get(item.serviceVisitId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.visits.put({
        serviceVisitId: item.serviceVisitId,
        diagnosticCaseId: item.diagnosticCaseId,
        technicianId: item.technician?.userId ?? '',
        technicianName: item.technician?.userName ?? '',
        visitStatus: item.visitStatus,
        visitKind: item.visitKind,
        scheduledAt: item.scheduledAt,
        startedAt: item.startedAt,
        endedAt: item.endedAt,
        notes: item.notes ?? '',
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** The checks of a visit are never archived, so no record is ever removed here. */
async function applyVisitSafetyChecks(items: VisitSafetyCheckChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.visitSafetyChecks, async () => {
    for (const item of items) {
      const existing = await database.visitSafetyChecks.get(item.serviceVisitId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.visitSafetyChecks.put({
        serviceVisitId: item.serviceVisitId,
        safetyChecks: item.safetyChecks ?? {},
        utilityBaseline: item.utilityBaseline ?? {},
        environmentBaseline: item.environmentBaseline ?? {},
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** A step is never archived, so no step is ever removed here. */
async function applyDiagnosticSteps(items: DiagnosticStepChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.diagnosticSteps, async () => {
    for (const item of items) {
      const existing = await database.diagnosticSteps.get(item.diagnosticStepId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.diagnosticSteps.put({
        diagnosticStepId: item.diagnosticStepId,
        serviceVisitId: item.serviceVisitId,
        stepNo: item.stepNo,
        hypothesis: item.hypothesis,
        testTarget: item.testTarget ?? '',
        powerState: item.powerState,
        expected: item.expected ?? '',
        actual: item.actual ?? '',
        result: item.result,
        nextStep: item.nextStep ?? '',
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** The completion record of a visit is never archived, so no record is ever removed here. */
async function applyVisitCompletions(items: VisitCompletionChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.visitCompletions, async () => {
    for (const item of items) {
      const existing = await database.visitCompletions.get(item.visitCompletionRecordId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.visitCompletions.put({
        visitCompletionRecordId: item.visitCompletionRecordId,
        serviceVisitId: item.serviceVisitId,
        verificationChecks: item.verificationChecks ?? {},
        finalStatus: item.finalStatus,
        fixOutcome: item.fixOutcome,
        googleReviewStatus: item.googleReviewStatus,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** The summary of a case is never archived, so no summary is ever removed here. */
async function applyCaseSummaries(items: CaseSummaryChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.caseSummaries, async () => {
    for (const item of items) {
      const existing = await database.caseSummaries.get(item.caseSummaryRecordId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.caseSummaries.put({
        caseSummaryRecordId: item.caseSummaryRecordId,
        diagnosticCaseId: item.diagnosticCaseId,
        diagnosticOutcome: item.diagnosticOutcome,
        repairStatus: item.repairStatus,
        customerDecision: item.customerDecision,
        failureCategories: item.failureCategories ?? [],
        primaryFailureCategory: item.primaryFailureCategory,
        dataTags: item.dataTags ?? '',
        technicianSummary: item.technicianSummary ?? '',
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** A bill is never archived (it can be void), so no bill is ever removed here. */
async function applyCaseInvoices(items: CaseInvoiceChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.caseInvoices, async () => {
    for (const item of items) {
      const existing = await database.caseInvoices.get(item.caseInvoiceId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.caseInvoices.put({
        caseInvoiceId: item.caseInvoiceId,
        diagnosticCaseId: item.diagnosticCaseId,
        invoiceStatus: item.invoiceStatus,
        issuedAt: item.issuedAt,
        serviceCallFee: item.serviceCallFee,
        labourFee: item.labourFee,
        partsFee: item.partsFee,
        discountAmount: item.discountAmount,
        taxRate: item.taxRate,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/** A payment is never archived, so no payment is ever removed here. */
async function applyCaseInvoicePayments(items: CaseInvoicePaymentChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.caseInvoicePayments, async () => {
    for (const item of items) {
      const existing = await database.caseInvoicePayments.get(item.caseInvoicePaymentId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.caseInvoicePayments.put({
        caseInvoicePaymentId: item.caseInvoicePaymentId,
        caseInvoiceId: item.caseInvoiceId,
        amount: item.amount,
        paymentMethod: item.paymentMethod,
        referenceNo: item.referenceNo ?? '',
        receivedAt: item.receivedAt,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}

/**
 * An attachment is never archived, so no row is ever removed here. Only the facts about the file are replaced:
 * the file that waits for its upload is kept in another table and is not touched.
 */
async function applyAttachments(items: AttachmentChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.attachments, async () => {
    for (const item of items) {
      const existing = await database.attachments.get(item.attachmentId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.attachments.put({
        attachmentId: item.attachmentId,
        entityType: item.entityType,
        entityId: item.entityId,
        mediaKind: item.mediaKind,
        uploadStatus: item.uploadStatus,
        fileName: item.fileName,
        mimeType: item.mimeType,
        byteSize: item.byteSize,
        capturedAt: item.capturedAt,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      })
    }
  })
}

/**
 * A document is never archived, so no row is ever removed here. Only the facts about the document are replaced:
 * the file that waits for its upload is kept in another table and is not touched.
 */
async function applyApplianceDocuments(items: ApplianceDocumentChange[]): Promise<void> {
  const database = db()
  await database.transaction('rw', database.applianceDocuments, async () => {
    for (const item of items) {
      const existing = await database.applianceDocuments.get(item.applianceDocumentId)
      if (existing && existing.syncState !== 'SYNCED') {
        continue
      }
      await database.applianceDocuments.put({
        applianceDocumentId: item.applianceDocumentId,
        documentType: item.documentType,
        documentRef: item.documentRef,
        uploadStatus: item.uploadStatus,
        fileName: item.fileName,
        mimeType: item.mimeType,
        byteSize: item.byteSize,
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      })
    }
  })
}
