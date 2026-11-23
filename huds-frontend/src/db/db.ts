import Dexie from 'dexie'
import type { EntityTable } from 'dexie'
import type {
  AddressSnapshot,
  ApiErrorBody,
  ApplianceDocumentType,
  ApplianceDocumentUploadStatus,
  AttachmentEntityType,
  AttachmentMediaKind,
  AttachmentUploadStatus,
  PartsSupplyStatus,
  CaseDisposition,
  CaseStatus,
  CustomerDecision,
  CustomerType,
  DiagnosticOutcome,
  EnvironmentBaseline,
  ErrorCodeStatus,
  FailureCategory,
  FailureTiming,
  FinalStatus,
  FixOutcome,
  GoogleReviewStatus,
  InvoiceStatus,
  PaymentMethod,
  PowerState,
  PrimarySymptom,
  RepairStatus,
  SafetyChecks,
  StepResult,
  SyncEntityType,
  SyncOperation,
  UserBrief,
  UtilityBaseline,
  VerificationChecks,
  VisitKind,
  VisitStatus,
} from '../api/types'

/**
 * PENDING  - waiting to be sent
 * SENDING  - part of a request that is on its way
 * BLOCKED  - the server did not run it because something it depends on failed (it can be sent again)
 * REJECTED - the server refused it; the user has to look at it
 */
export type OutboxStatus = 'PENDING' | 'SENDING' | 'BLOCKED' | 'REJECTED'

/** One change the user made on this device that the server does not know yet. */
export interface OutboxItem {
  /** Order in which the changes were made. */
  seq: number
  mutationId: string
  entityType: SyncEntityType
  entityId: string
  operation: SyncOperation
  rowVersion?: number
  payload?: unknown
  clientRecordedAt: string
  dependsOn: string[]
  status: OutboxStatus
  error?: ApiErrorBody
}

export type SyncState = 'SYNCED' | 'PENDING' | 'REJECTED'

/** Who created and last changed a record, and when. Filled by the download; unknown for records that exist only on this device. */
export interface AuditFields {
  createdAt?: string
  updatedAt?: string
  createdByName?: string
  updatedByName?: string
}

/** Fields every local copy has. */
interface LocalRecordBase extends AuditFields {
  /** Unknown until the server has accepted the record for the first time. */
  rowVersion?: number
  syncState: SyncState
  /** The user archived the record and the archive is waiting to be sent. Such a record is hidden from the lists. */
  archiving?: boolean
}

/** The local copy of a customer. */
export interface LocalCustomer extends LocalRecordBase {
  customerId: string
  customerName: string
  customerType: CustomerType
  email: string
  phone: string
  notes: string
}

/** The local copy of a service address. */
export interface LocalAddress extends LocalRecordBase {
  serviceAddressId: string
  customerId: string
  addressLine1: string
  addressLine2: string
  city: string
  province: string
  postalCode: string
  countryCode: string
  isDefault: boolean
}

/** The local copy of an appliance model (the catalog entry). */
export interface LocalModel extends LocalRecordBase {
  applianceModelId: string
  applianceType: string
  brand: string
  modelNo: string
  /** The schematic and the service manual follow the model, not the single appliance. */
  schematicDocumentId: string | null
  /** Where the wiring diagram is on the appliance. */
  schematicLocation: string
  serviceManualDocumentId: string | null
}

/** The local copy of an appliance (one physical unit of a model). */
export interface LocalAppliance extends LocalRecordBase {
  applianceId: string
  /** The owner. Fixed when the appliance is created. */
  customerId: string
  applianceModelId: string
  serialNo: string
  serialVerified: boolean
  identityNotes: string
  customerAssetRef: string
  purchaseOrderNumber: string
  productionDate: string | null
  estimatedAgeYears: number | null
}

/**
 * The local copy of a case. The case number is given by the server, so it is empty
 * until the case has been accepted and downloaded. workflowStatus shows the state the user chose,
 * even while that change still waits to be sent.
 */
export interface LocalCase extends LocalRecordBase {
  diagnosticCaseId: string
  caseNo: string
  customerId: string
  serviceAddressId: string
  applianceId: string
  serviceAddressSnapshot: AddressSnapshot
  reportedSymptoms: string
  primarySymptom: PrimarySymptom | null
  secondarySymptoms: PrimarySymptom[]
  failureTiming: FailureTiming
  workflowStatus: CaseStatus
  disposition: CaseDisposition | null
  holdReason: string
  relatedCaseId: string | null
  openedAt: string
  closedAt: string | null
}

/** The local copy of an error code of a case. */
export interface LocalCaseErrorCode extends LocalRecordBase {
  caseErrorCodeId: string
  diagnosticCaseId: string
  errorCode: string
  displayMessage: string
  codeStatus: ErrorCodeStatus
}

/** The local copy of a part that a case needs (to wait for, or already in hand). */
export interface LocalCasePart extends LocalRecordBase {
  casePartId: string
  diagnosticCaseId: string
  partName: string
  oemPartNo: string
  quantity: number
  unit: string
  supplier: string
  partsSupplyStatus: PartsSupplyStatus
  orderedOn: string | null
  expectedArrivalOn: string | null
  arrivedOn: string | null
  /** Warranty after installation, in months. */
  warrantyMonths: number | null
  installedVisitId: string | null
}

/**
 * The local copy of a visit. visitStatus shows the state the user chose, even while that change still waits to be sent.
 * technicianName is kept next to the id so the visit can be read without a lookup.
 */
export interface LocalVisit extends LocalRecordBase {
  serviceVisitId: string
  diagnosticCaseId: string
  technicianId: string
  technicianName: string
  visitStatus: VisitStatus
  visitKind: VisitKind
  scheduledAt: string | null
  startedAt: string | null
  endedAt: string | null
  notes: string
}

/** The local copy of the safety and baseline checks of a visit. Its id is the id of the visit. */
export interface LocalVisitSafetyCheck extends LocalRecordBase {
  serviceVisitId: string
  safetyChecks: SafetyChecks
  utilityBaseline: UtilityBaseline
  environmentBaseline: EnvironmentBaseline
}

/** The local copy of one diagnostic step of a visit (section 3). Steps are numbered from 1 within the visit. */
export interface LocalDiagnosticStep extends LocalRecordBase {
  diagnosticStepId: string
  serviceVisitId: string
  stepNo: number
  hypothesis: string
  testTarget: string
  powerState: PowerState | null
  expected: string
  actual: string
  result: StepResult | null
  nextStep: string
}

/** The local copy of the completion record of a visit (section 5). It has an id of its own. */
export interface LocalVisitCompletion extends LocalRecordBase {
  visitCompletionRecordId: string
  serviceVisitId: string
  verificationChecks: VerificationChecks
  finalStatus: FinalStatus | null
  fixOutcome: FixOutcome | null
  googleReviewStatus: GoogleReviewStatus | null
}

/** The local copy of the summary of a case (sections 6 and 7). */
export interface LocalCaseSummary extends LocalRecordBase {
  caseSummaryRecordId: string
  diagnosticCaseId: string
  diagnosticOutcome: DiagnosticOutcome | null
  repairStatus: RepairStatus | null
  customerDecision: CustomerDecision | null
  failureCategories: FailureCategory[]
  primaryFailureCategory: FailureCategory | null
  dataTags: string
  technicianSummary: string
}

/** The local copy of the bill of a case. The subtotal, tax and total are worked out from the fees when they are shown. */
export interface LocalCaseInvoice extends LocalRecordBase {
  caseInvoiceId: string
  diagnosticCaseId: string
  invoiceStatus: InvoiceStatus
  issuedAt: string | null
  serviceCallFee: number
  labourFee: number
  partsFee: number
  discountAmount: number
  taxRate: number
}

/** The local copy of one payment received for a bill. */
export interface LocalCaseInvoicePayment extends LocalRecordBase {
  caseInvoicePaymentId: string
  caseInvoiceId: string
  amount: number
  paymentMethod: PaymentMethod
  referenceNo: string
  receivedAt: string
}

/** The local copy of an attachment (the facts about a file). The file itself waits in LocalAttachmentFile until it is uploaded. */
export interface LocalAttachment extends LocalRecordBase {
  attachmentId: string
  /** What the file belongs to. */
  entityType: AttachmentEntityType
  entityId: string
  mediaKind: AttachmentMediaKind
  uploadStatus: AttachmentUploadStatus
  fileName: string
  mimeType: string
  byteSize: number
  capturedAt: string | null
}

/**
 * The bytes of a file that has not been uploaded yet. Kept apart from LocalAttachment so the lists stay light
 * and so the download of the server's version never touches the file. Removed when the upload is complete.
 */
export interface LocalAttachmentFile {
  attachmentId: string
  file: Blob
  /** Why the last upload attempt failed, shown to the user. */
  lastError?: string
}

/** The local copy of a registered document (a schematic or a service manual). The file waits in LocalApplianceDocumentFile until it is uploaded. */
export interface LocalApplianceDocument extends LocalRecordBase {
  applianceDocumentId: string
  documentType: ApplianceDocumentType
  /** The reference number printed on the document. Unique per type on the server. */
  documentRef: string
  uploadStatus: ApplianceDocumentUploadStatus
  /** Unknown until a file has been uploaded. */
  fileName: string | null
  mimeType: string | null
  byteSize: number | null
}

/** The bytes of a document that has not been uploaded yet. Removed when the upload is complete. */
export interface LocalApplianceDocumentFile {
  applianceDocumentId: string
  file: Blob
  /** The name of the file as the user's device gave it. */
  fileName: string
  /** Calculated when the file was chosen; the upload link is signed for it. */
  sha256Hex: string
  /** Why the last upload attempt failed, shown to the user. */
  lastError?: string
}

/** A user of the system. Read only: downloaded from the server. */
export interface LocalUser {
  userId: string
  userName: string
  role: string
}

/**
 * One state change of any entity (a case now, a visit later), as the server recorded it. Read only on this device.
 * The states are plain text here because every entity type has its own list of states.
 */
export interface LocalStateEvent {
  stateEventId: string
  /** What changed state: 'CASE', 'VISIT', ... */
  entityType: string
  entityId: string
  fromState: string | null
  toState: string
  reason: string
  actor: UserBrief | null
  occurredAt: string
  clientRecordedAt: string | null
}

/** Where the download of one entity type stopped last time. */
export interface SyncCursor {
  /** "cursor:" + entity type */
  key: string
  since: string
  sinceId: string
}

export type HudsDatabase = Dexie & {
  outbox: EntityTable<OutboxItem, 'seq'>
  customers: EntityTable<LocalCustomer, 'customerId'>
  addresses: EntityTable<LocalAddress, 'serviceAddressId'>
  models: EntityTable<LocalModel, 'applianceModelId'>
  appliances: EntityTable<LocalAppliance, 'applianceId'>
  cases: EntityTable<LocalCase, 'diagnosticCaseId'>
  caseErrorCodes: EntityTable<LocalCaseErrorCode, 'caseErrorCodeId'>
  caseParts: EntityTable<LocalCasePart, 'casePartId'>
  stateEvents: EntityTable<LocalStateEvent, 'stateEventId'>
  visits: EntityTable<LocalVisit, 'serviceVisitId'>
  visitSafetyChecks: EntityTable<LocalVisitSafetyCheck, 'serviceVisitId'>
  diagnosticSteps: EntityTable<LocalDiagnosticStep, 'diagnosticStepId'>
  visitCompletions: EntityTable<LocalVisitCompletion, 'visitCompletionRecordId'>
  caseSummaries: EntityTable<LocalCaseSummary, 'caseSummaryRecordId'>
  caseInvoices: EntityTable<LocalCaseInvoice, 'caseInvoiceId'>
  caseInvoicePayments: EntityTable<LocalCaseInvoicePayment, 'caseInvoicePaymentId'>
  attachments: EntityTable<LocalAttachment, 'attachmentId'>
  attachmentFiles: EntityTable<LocalAttachmentFile, 'attachmentId'>
  applianceDocuments: EntityTable<LocalApplianceDocument, 'applianceDocumentId'>
  applianceDocumentFiles: EntityTable<LocalApplianceDocumentFile, 'applianceDocumentId'>
  users: EntityTable<LocalUser, 'userId'>
  meta: EntityTable<SyncCursor, 'key'>
}

let current: HudsDatabase | null = null

/**
 * Opens the local database of one user. Every user has a database of their own,
 * so the outbox of one person can never be sent under the name of another.
 */
export function openDatabase(userId: string): HudsDatabase {
  closeDatabase()
  const database = new Dexie(`huds-${userId}`) as HudsDatabase
  // "++seq" = automatic, increasing number; the other names are indexes.
  // Every version lists all tables. Never change an old version: add a new one.
  database.version(1).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
  })
  database.version(2).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
  })
  database.version(3).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, applianceModelId',
    meta: 'key',
  })
  database.version(4).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, applianceModelId',
    cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
    caseEvents: 'caseStateEventId, diagnosticCaseId',
    meta: 'key',
  })
  // Version 5: an appliance belongs to one customer. Appliances downloaded earlier have no customerId,
  // so the download cursor is forgotten and the server's version of every appliance is fetched again.
  database
    .version(5)
    .stores({
      outbox: '++seq, mutationId, entityId, status',
      customers: 'customerId, customerName, email',
      addresses: 'serviceAddressId, customerId',
      models: 'applianceModelId, applianceType',
      appliances: 'applianceId, customerId, applianceModelId',
      cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
      caseEvents: 'caseStateEventId, diagnosticCaseId',
      meta: 'key',
    })
    .upgrade(async (transaction) => {
      await transaction.table('meta').delete('cursor:APPLIANCE')
    })
  // Version 6: the history of a case moved into a table shared by all entities (stateEvents).
  // Events are only ever downloaded, so nothing is lost: the old table is dropped and the new one is
  // filled by a full download of the STATE_EVENT type.
  database
    .version(6)
    .stores({
      outbox: '++seq, mutationId, entityId, status',
      customers: 'customerId, customerName, email',
      addresses: 'serviceAddressId, customerId',
      models: 'applianceModelId, applianceType',
      appliances: 'applianceId, customerId, applianceModelId',
      cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
      caseEvents: null,
      stateEvents: 'stateEventId, entityId',
      meta: 'key',
    })
    .upgrade(async (transaction) => {
      await transaction.table('meta').delete('cursor:CASE_STATE_EVENT')
    })
  // Version 7 (M3): visits, safety assessments and a list of technicians. New tables only, nothing to convert.
  database.version(7).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, customerId, applianceModelId',
    cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
    stateEvents: 'stateEventId, entityId',
    visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
    safetyAssessments: 'safetyAssessmentId, serviceVisitId',
    technicians: 'userId',
    meta: 'key',
  })
  // Version 8: the list of technicians became the list of all users (with their role). The old table is dropped;
  // the new one is filled by a full download of the USER type.
  database
    .version(8)
    .stores({
      outbox: '++seq, mutationId, entityId, status',
      customers: 'customerId, customerName, email',
      addresses: 'serviceAddressId, customerId',
      models: 'applianceModelId, applianceType',
      appliances: 'applianceId, customerId, applianceModelId',
      cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
      stateEvents: 'stateEventId, entityId',
      visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
      safetyAssessments: 'safetyAssessmentId, serviceVisitId',
      technicians: null,
      users: 'userId',
      meta: 'key',
    })
    .upgrade(async (transaction) => {
      await transaction.table('meta').delete('cursor:TECHNICIAN')
    })
  // Version 9: safety assessments are not part of the product for now. The table is dropped.
  database
    .version(9)
    .stores({
      outbox: '++seq, mutationId, entityId, status',
      customers: 'customerId, customerName, email',
      addresses: 'serviceAddressId, customerId',
      models: 'applianceModelId, applianceType',
      appliances: 'applianceId, customerId, applianceModelId',
      cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
      stateEvents: 'stateEventId, entityId',
      visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
      safetyAssessments: null,
      users: 'userId',
      meta: 'key',
    })
    .upgrade(async (transaction) => {
      await transaction.table('meta').delete('cursor:SAFETY_ASSESSMENT')
      // An assessment change that is still waiting in the outbox can never be sent now, so it is dropped.
      await transaction.table('outbox').toCollection().filter((item) => item.entityType === 'SAFETY_ASSESSMENT').delete()
    })
  // Version 10: customer type, appliance details, case symptom classification, case error codes (own table) and visit kind.
  // The old error code text of a case is dropped. The cursors of the changed types are forgotten, so the server's
  // version of every record is downloaded again (records still waiting in the outbox are left alone by the download).
  database
    .version(10)
    .stores({
      outbox: '++seq, mutationId, entityId, status',
      customers: 'customerId, customerName, email',
      addresses: 'serviceAddressId, customerId',
      models: 'applianceModelId, applianceType',
      appliances: 'applianceId, customerId, applianceModelId',
      cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
      caseErrorCodes: 'caseErrorCodeId, diagnosticCaseId',
      stateEvents: 'stateEventId, entityId',
      visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
      users: 'userId',
      meta: 'key',
    })
    .upgrade(async (transaction) => {
      await transaction.table('customers').toCollection().modify((record) => {
        record.customerType ??= 'RETAIL_COD'
      })
      await transaction.table('appliances').toCollection().modify((record) => {
        record.customerAssetRef ??= ''
        record.purchaseOrderNumber ??= ''
        record.productionDate ??= null
        record.estimatedAgeYears ??= null
        record.schematicDocumentId ??= null
        record.schematicLocation ??= ''
        record.serviceManualDocumentId ??= null
      })
      await transaction.table('cases').toCollection().modify((record) => {
        delete record.errorCodes
        record.primarySymptom ??= null
        record.secondarySymptoms ??= []
        record.failureTiming ??= {}
      })
      await transaction.table('visits').toCollection().modify((record) => {
        record.visitKind ??= 'FIRST'
      })
      for (const type of ['CUSTOMER', 'APPLIANCE', 'CASE', 'SERVICE_VISIT']) {
        await transaction.table('meta').delete(`cursor:${type}`)
      }
    })
  // Version 11: the safety and baseline checks of a visit. A new table only, nothing to convert.
  database.version(11).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, customerId, applianceModelId',
    cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
    caseErrorCodes: 'caseErrorCodeId, diagnosticCaseId',
    stateEvents: 'stateEventId, entityId',
    visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
    visitSafetyChecks: 'serviceVisitId',
    users: 'userId',
    meta: 'key',
  })
  // Version 12: diagnostic steps and the completion record of a visit, the summary of a case, the bill of a case and its payments.
  // New tables only, nothing to convert.
  database.version(12).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, customerId, applianceModelId',
    cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
    caseErrorCodes: 'caseErrorCodeId, diagnosticCaseId',
    stateEvents: 'stateEventId, entityId',
    visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
    visitSafetyChecks: 'serviceVisitId',
    diagnosticSteps: 'diagnosticStepId, serviceVisitId',
    visitCompletions: 'visitCompletionRecordId, serviceVisitId',
    caseSummaries: 'caseSummaryRecordId, diagnosticCaseId',
    caseInvoices: 'caseInvoiceId, diagnosticCaseId',
    caseInvoicePayments: 'caseInvoicePaymentId, caseInvoiceId',
    users: 'userId',
    meta: 'key',
  })
  // Version 13: attachments (photos and videos) and the files that wait to be uploaded. New tables only, nothing to convert.
  database.version(13).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, customerId, applianceModelId',
    cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
    caseErrorCodes: 'caseErrorCodeId, diagnosticCaseId',
    stateEvents: 'stateEventId, entityId',
    visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
    visitSafetyChecks: 'serviceVisitId',
    diagnosticSteps: 'diagnosticStepId, serviceVisitId',
    visitCompletions: 'visitCompletionRecordId, serviceVisitId',
    caseSummaries: 'caseSummaryRecordId, diagnosticCaseId',
    caseInvoices: 'caseInvoiceId, diagnosticCaseId',
    caseInvoicePayments: 'caseInvoicePaymentId, caseInvoiceId',
    attachments: 'attachmentId, entityId',
    attachmentFiles: 'attachmentId',
    users: 'userId',
    meta: 'key',
  })
  // Version 14: the schematic and the service manual follow the appliance model instead of the single appliance.
  // New tables for the documents and their waiting files. Models get the three new fields, appliances lose them,
  // and the cursor of the models is forgotten so the server's version of every model is downloaded again.
  database
    .version(14)
    .stores({
      outbox: '++seq, mutationId, entityId, status',
      customers: 'customerId, customerName, email',
      addresses: 'serviceAddressId, customerId',
      models: 'applianceModelId, applianceType',
      appliances: 'applianceId, customerId, applianceModelId',
      cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
      caseErrorCodes: 'caseErrorCodeId, diagnosticCaseId',
      stateEvents: 'stateEventId, entityId',
      visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
      visitSafetyChecks: 'serviceVisitId',
      diagnosticSteps: 'diagnosticStepId, serviceVisitId',
      visitCompletions: 'visitCompletionRecordId, serviceVisitId',
      caseSummaries: 'caseSummaryRecordId, diagnosticCaseId',
      caseInvoices: 'caseInvoiceId, diagnosticCaseId',
      caseInvoicePayments: 'caseInvoicePaymentId, caseInvoiceId',
      attachments: 'attachmentId, entityId',
      attachmentFiles: 'attachmentId',
      applianceDocuments: 'applianceDocumentId, documentType',
      applianceDocumentFiles: 'applianceDocumentId',
      users: 'userId',
      meta: 'key',
    })
    .upgrade(async (transaction) => {
      await transaction.table('models').toCollection().modify((record) => {
        record.schematicDocumentId ??= null
        record.schematicLocation ??= ''
        record.serviceManualDocumentId ??= null
      })
      await transaction.table('appliances').toCollection().modify((record) => {
        delete record.schematicDocumentId
        delete record.schematicLocation
        delete record.serviceManualDocumentId
      })
      // An appliance change that still waits in the outbox must not carry the removed fields.
      await transaction
        .table('outbox')
        .toCollection()
        .filter((item) => item.entityType === 'APPLIANCE' && item.payload)
        .modify((item) => {
          delete item.payload.schematicDocumentId
          delete item.payload.schematicLocation
          delete item.payload.serviceManualDocumentId
        })
      await transaction.table('meta').delete('cursor:APPLIANCE_MODEL')
    })
  // Version 15: the parts of a case. A new table only, nothing to convert.
  database.version(15).stores({
    outbox: '++seq, mutationId, entityId, status',
    customers: 'customerId, customerName, email',
    addresses: 'serviceAddressId, customerId',
    models: 'applianceModelId, applianceType',
    appliances: 'applianceId, customerId, applianceModelId',
    cases: 'diagnosticCaseId, customerId, serviceAddressId, applianceId, workflowStatus',
    caseErrorCodes: 'caseErrorCodeId, diagnosticCaseId',
    caseParts: 'casePartId, diagnosticCaseId',
    stateEvents: 'stateEventId, entityId',
    visits: 'serviceVisitId, diagnosticCaseId, technicianId, visitStatus',
    visitSafetyChecks: 'serviceVisitId',
    diagnosticSteps: 'diagnosticStepId, serviceVisitId',
    visitCompletions: 'visitCompletionRecordId, serviceVisitId',
    caseSummaries: 'caseSummaryRecordId, diagnosticCaseId',
    caseInvoices: 'caseInvoiceId, diagnosticCaseId',
    caseInvoicePayments: 'caseInvoicePaymentId, caseInvoiceId',
    attachments: 'attachmentId, entityId',
    attachmentFiles: 'attachmentId',
    applianceDocuments: 'applianceDocumentId, documentType',
    applianceDocumentFiles: 'applianceDocumentId',
    users: 'userId',
    meta: 'key',
  })
  current = database
  return database
}

export function closeDatabase(): void {
  if (current) {
    current.close()
    current = null
  }
}

/** The database of the logged-in user. */
export function db(): HudsDatabase {
  if (!current) {
    throw new Error('The local database is not open. Log in first.')
  }
  return current
}
