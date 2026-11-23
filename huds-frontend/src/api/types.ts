// Shapes of the JSON that huds-backend sends and receives.
// Keep them in step with the backend DTOs (records) of the same name.

export type ClientType = 'DESKTOP' | 'MOBILE'

/** Response of POST /api/v1/auth/login and GET /api/v1/auth/me (the part we use). */
export interface CurrentUser {
  userId: string
  userName: string
  email: string
  role: string
  permissions: string[]
}

export interface FieldViolation {
  field: string
  message: string
}

/** The error body of every failed request (ApiErrorResponse). */
export interface ApiErrorBody {
  code: string
  message: string
  retryable: boolean
  currentVersion: number | null
  correlationId: string | null
  conflictingResourceId: string | null
  fieldErrors?: FieldViolation[] | null
}

/** A user as the server describes the creator or last editor of a record. */
export interface UserBrief {
  userId: string
  userName: string
}

// ---- Sync pull: one record of GET /api/v1/sync/pull. Archived records come with archived=true. ----

interface PulledAudit {
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
  archived: boolean
}

/** Who the customer is for billing and for how a visit is paid. */
export type CustomerType = 'RETAIL_COD' | 'PROPERTY_LANDLORD' | 'THIRD_PARTY_WARRANTY' | 'OEM_FACTORY'

/** The root decision branch of a case (one of seven). */
export type PrimarySymptom =
  | 'NO_POWER'
  | 'NO_START'
  | 'NO_HEAT_COOL'
  | 'LEAK_DRAIN'
  | 'NOISE_VIBRATION'
  | 'STOPS_MID_CYCLE'
  | 'INTERMITTENT'

/** FIRST visit of a case, a later FOLLOW_UP (for example to fit parts), or a RECALL after an earlier repair. */
export type VisitKind = 'FIRST' | 'FOLLOW_UP' | 'RECALL'

/** An error code that is still shown on the appliance (ACTIVE) or only kept as history. */
export type ErrorCodeStatus = 'ACTIVE' | 'STORED_HISTORY'

/**
 * When the fault shows. The content is decided here, the server only checks that it is a JSON object.
 * phase: when in the cycle it happens; afterMinutes: how long after the start (optional).
 */
export interface FailureTiming {
  phase?: string
  afterMinutes?: number | null
}

export interface CustomerChange extends PulledAudit {
  customerId: string
  customerName: string
  customerType: CustomerType
  email: string | null
  phone: string | null
  notes: string | null
}

export interface ServiceAddressChange extends PulledAudit {
  serviceAddressId: string
  customerId: string
  addressLine1: string
  addressLine2: string | null
  city: string
  province: string
  postalCode: string | null
  countryCode: string
  isDefault: boolean
}

export interface ApplianceModelChange extends PulledAudit {
  applianceModelId: string
  applianceType: string
  brand: string | null
  modelNo: string | null
  schematicDocumentId: string | null
  schematicLocation: string | null
  serviceManualDocumentId: string | null
}

export interface ApplianceChange extends PulledAudit {
  applianceId: string
  customerId: string
  applianceModelId: string
  serialNo: string | null
  serialVerified: boolean
  identityNotes: string | null
  customerAssetRef: string | null
  purchaseOrderNumber: string | null
  /** yyyy-MM-dd */
  productionDate: string | null
  estimatedAgeYears: number | null
}

/** The states a case moves through (see the case state machine in the backend spec). */
export type CaseStatus =
  | 'OPEN'
  | 'DIAGNOSING'
  | 'PENDING_AUTHORIZATION'
  | 'PENDING_PARTS'
  | 'IN_REPAIR'
  | 'ON_HOLD'
  | 'CLOSED'
  | 'CANCELLED'

/** How a closed case ended. CANCELLED is only set by the server when a case is cancelled. */
export type CaseDisposition =
  | 'FIXED_VERIFIED'
  | 'UNRESOLVED'
  | 'CUSTOMER_DECLINED'
  | 'BER'
  | 'UNSAFE'
  | 'NO_FAULT_FOUND'
  | 'REFERRED'
  | 'CANCELLED'

/** The address as it was when the case was opened. Filled by the server; never sent by the client. */
export interface AddressSnapshot {
  addressLine1?: string
  addressLine2?: string | null
  city?: string
  province?: string
  postalCode?: string | null
  countryCode?: string
}

/** A case has no archived flag: a case is never archived. */
export interface CaseChange {
  diagnosticCaseId: string
  caseNo: string
  customerId: string
  serviceAddressId: string
  applianceId: string
  serviceAddressSnapshot: AddressSnapshot | null
  reportedSymptoms: string
  primarySymptom: PrimarySymptom | null
  secondarySymptoms: PrimarySymptom[] | null
  failureTiming: FailureTiming | null
  workflowStatus: CaseStatus
  disposition: CaseDisposition | null
  holdReason: string | null
  relatedCaseId: string | null
  openedAt: string
  closedAt: string | null
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

/** An error code of a case. Never deleted: set the status to STORED_HISTORY instead. */
export interface CaseErrorCodeChange {
  caseErrorCodeId: string
  diagnosticCaseId: string
  errorCode: string
  displayMessage: string | null
  codeStatus: ErrorCodeStatus
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

/** Where a part of a case is in its supply. ARRIVED: ordered or on backorder, and now in hand. */
export type PartsSupplyStatus = 'IN_STOCK' | 'ORDERED' | 'BACKORDER' | 'CUSTOMER_SUPPLIED' | 'ARRIVED'

/** A part of a case as the server sends it. Archived parts come with archived=true. */
export interface CasePartChange extends PulledAudit {
  casePartId: string
  diagnosticCaseId: string
  partName: string
  oemPartNo: string | null
  quantity: number
  unit: string | null
  supplier: string | null
  partsSupplyStatus: PartsSupplyStatus
  /** yyyy-MM-dd */
  orderedOn: string | null
  expectedArrivalOn: string | null
  arrivedOn: string | null
  /** Warranty of the part after it is installed, in months. */
  warrantyMonths: number | null
  installedVisitId: string | null
}

/** One state change of any entity (case, visit, ...). Written by the server only and never changed afterwards. */
export interface StateEventChange {
  stateEventId: string
  entityType: string
  entityId: string
  fromState: string | null
  toState: string
  reason: string | null
  actor: UserBrief | null
  occurredAt: string
  clientRecordedAt: string | null
}

/** The states of a visit (see the visit state machine in the backend spec). */
export type VisitStatus = 'PLANNED' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'

/** A visit has no archived flag: a visit is cancelled, never archived. */
export interface VisitChange {
  serviceVisitId: string
  diagnosticCaseId: string
  technician: UserBrief | null
  visitStatus: VisitStatus
  visitKind: VisitKind
  scheduledAt: string | null
  startedAt: string | null
  endedAt: string | null
  notes: string | null
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

/**
 * What the technician records before the repair (section 2 of the diagnostic form).
 * The id of a safety check record is the id of its visit. The content of the three objects is decided here;
 * the server only checks that each one is a JSON object.
 */
export type SafetyCheckCode =
  | 'ELECTRICAL_ISOLATION'
  | 'LOTO'
  | 'POWER_BREAKER'
  | 'CORD_PLUG'
  | 'WATER_DRAIN_VENT'
  | 'LEAK_MOISTURE'
  | 'BURN_ODOR'
  | 'MECHANICAL_ACCESS'

/** The ticked checks. A check that is missing was not done or not recorded. */
export type SafetyChecks = Partial<Record<SafetyCheckCode, boolean>>

/** Supply type and voltages. */
export interface UtilityBaseline {
  supplyType?: 'V120_1PH' | 'V120_240_SPLIT'
  l1nV?: number
  l2nV?: number
  l1l2V?: number
}

/** Water, gas and ambient notes. */
export interface EnvironmentBaseline {
  waterTempF?: number
  pressureStaticPsi?: number
  pressureDynamicPsi?: number
  gas?: 'NG' | 'LP'
  ambientNotes?: string
}

/** The safety and baseline checks of a visit. Never archived. */
export interface VisitSafetyCheckChange {
  serviceVisitId: string
  safetyChecks: SafetyChecks | null
  utilityBaseline: UtilityBaseline | null
  environmentBaseline: EnvironmentBaseline | null
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

// ---- Records of a visit (sections 3 and 5) and of a case (sections 6 and 7, the bill) ----

export type PowerState = 'STATIC' | 'LIVE'
export type StepResult = 'PASS' | 'FAIL'

/** One hypothesis tested during a visit (section 3). Never archived. */
export interface DiagnosticStepChange {
  diagnosticStepId: string
  serviceVisitId: string
  stepNo: number
  hypothesis: string
  testTarget: string | null
  powerState: PowerState | null
  expected: string | null
  actual: string | null
  result: StepResult | null
  nextStep: string | null
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

export type VerificationCheckCode =
  | 'CURRENT_AMPS_IN_SPEC'
  | 'RELEVANT_CYCLE_COMPLETED'
  | 'NO_LEAK_VERIFIED'
  | 'GROUND_SAFETY_CHECKED'
export type VerificationChecks = Partial<Record<VerificationCheckCode, boolean>>

export type FinalStatus =
  | 'OPERATING_NORMALLY'
  | 'FURTHER_TESTING'
  | 'QUOTE_PENDING_APPROVAL'
  | 'PARTS_REQUIRED'
  | 'NO_FAULT_FOUND'
  | 'REPAIR_DECLINED'
  | 'NOT_REPAIRABLE'
export type FixOutcome = 'FIRST_TIME_FIX' | 'RETURN_VISIT_REQUIRED' | 'RECALL_RESOLVED'
export type GoogleReviewStatus = 'NOT_REQUESTED' | 'PENDING' | 'REQUESTED' | 'COMPLETED' | 'DECLINED'

/** The completion record of a visit (section 5). Never archived. */
export interface VisitCompletionChange {
  visitCompletionRecordId: string
  serviceVisitId: string
  verificationChecks: VerificationChecks | null
  finalStatus: FinalStatus | null
  fixOutcome: FixOutcome | null
  googleReviewStatus: GoogleReviewStatus | null
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

export type DiagnosticOutcome = 'FAULT_CONFIRMED' | 'NO_TROUBLE_FOUND' | 'BEYOND_ECONOMICAL_REPAIR'
export type RepairStatus = 'REPAIRED' | 'PENDING_PARTS' | 'INCOMPLETE'
export type CustomerDecision = 'APPROVED' | 'DECLINED' | 'PENDING'
export type FailureCategory = 'PWR' | 'CTRL' | 'SENS' | 'MTR' | 'PMP' | 'HEAT' | 'REFRIG' | 'WTR_DRN' | 'MECH' | 'WIRE'

/** The summary of a case (sections 6 and 7). Never archived. */
export interface CaseSummaryChange {
  caseSummaryRecordId: string
  diagnosticCaseId: string
  diagnosticOutcome: DiagnosticOutcome | null
  repairStatus: RepairStatus | null
  customerDecision: CustomerDecision | null
  failureCategories: FailureCategory[] | null
  primaryFailureCategory: FailureCategory | null
  dataTags: string | null
  technicianSummary: string | null
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'VOID'
export type PaymentMethod = 'CASH' | 'DEBIT_CARD' | 'CREDIT_CARD' | 'E_TRANSFER' | 'CHEQUE'

/** The bill of a case. Amounts are in CAD. The subtotal, tax and total are worked out by the server and by this device. */
export interface CaseInvoiceChange {
  caseInvoiceId: string
  diagnosticCaseId: string
  invoiceStatus: InvoiceStatus
  issuedAt: string | null
  serviceCallFee: number
  labourFee: number
  partsFee: number
  discountAmount: number
  taxRate: number
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

/** One payment received for a bill. Never archived. */
export interface CaseInvoicePaymentChange {
  caseInvoicePaymentId: string
  caseInvoiceId: string
  amount: number
  paymentMethod: PaymentMethod
  referenceNo: string | null
  receivedAt: string
  rowVersion: number
  createdAt: string
  updatedAt: string
  createdBy: UserBrief | null
  updatedBy: UserBrief | null
}

/** A user of the system (name and role only). Read only. */
export interface UserChange {
  userId: string
  userName: string
  /** TECHNICIAN, REVIEWER or ADMIN. */
  role: string
  /** A user who was deactivated is removed from the list on this device. */
  active: boolean
}

export interface PullResponse<T> {
  items: T[]
  nextSince: string
  nextSinceId: string
  hasMore: boolean
}

// ---- Attachments: photos and videos of a case, an appliance, a visit or a diagnostic step ----

export type AttachmentEntityType = 'CASE' | 'APPLIANCE' | 'SERVICE_VISIT' | 'DIAGNOSTIC_STEP'
export type AttachmentMediaKind = 'PHOTO' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'SIGNATURE'
export type AttachmentUploadStatus = 'PENDING' | 'UPLOADING' | 'UPLOADED' | 'VERIFIED' | 'FAILED'

/** One attachment as the server sends it (download and complete-upload). Never archived. */
export interface AttachmentChange {
  attachmentId: string
  entityType: AttachmentEntityType
  entityId: string
  mediaKind: AttachmentMediaKind
  uploadStatus: AttachmentUploadStatus
  fileName: string
  mimeType: string
  byteSize: number
  rowVersion: number
  capturedAt: string | null
  createdAt: string
  updatedAt: string
}

/** The body of an ATTACHMENT REGISTER mutation (AttachmentRequest). The sha256 is calculated on this device. */
export interface AttachmentPayload {
  entityType: AttachmentEntityType
  entityId: string
  fileName: string
  mimeType: string
  byteSize: number
  sha256Hex: string
  capturedAt: string | null
}

/** A short-lived address the file is sent to. The headers have to be sent with the file exactly as given. */
export interface PresignedUpload {
  url: string
  expiresAt: string
  headers: Record<string, string>
}

/** A short-lived address the file can be opened from. */
export interface AttachmentDownloadLink {
  url: string
  expiresAt: string
}

// ---- Appliance documents: the schematic and the service manual of a model ----

export type ApplianceDocumentType = 'SCHEMATIC' | 'SERVICE_MANUAL' | 'OTHER'

/** NO_FILE: only the reference number is registered. PENDING: an upload link was issued. UPLOADED: the file is in storage. */
export type ApplianceDocumentUploadStatus = 'NO_FILE' | 'PENDING' | 'UPLOADED'

/** One document as the server sends it (download, complete-upload). Never archived. */
export interface ApplianceDocumentChange {
  applianceDocumentId: string
  documentType: ApplianceDocumentType
  documentRef: string
  uploadStatus: ApplianceDocumentUploadStatus
  fileName: string | null
  mimeType: string | null
  byteSize: number | null
  rowVersion: number
  createdAt: string
  updatedAt: string
}

/** The body of an APPLIANCE_DOCUMENT REGISTER mutation (ApplianceDocumentRequest). The file is uploaded in a later step. */
export interface ApplianceDocumentPayload {
  documentType: ApplianceDocumentType
  documentRef: string
}

/** The body of the upload-url request (ApplianceDocumentUploadRequest). The server signs the link for exactly this size and type. */
export interface ApplianceDocumentUploadPayload {
  fileName: string
  mimeType: string
  byteSize: number
  sha256Hex: string
}

/** The answer to the upload-url request (ApplianceDocumentUploadResponse). */
export interface ApplianceDocumentUploadResult {
  document: ApplianceDocumentChange
  upload: PresignedUpload
}

/** The body of a CUSTOMER CREATE or UPDATE mutation (CustomerRequest). */
export interface CustomerPayload {
  customerName: string
  customerType: CustomerType
  email: string
  phone: string
  notes: string
}

/** The body of a SERVICE_ADDRESS CREATE or UPDATE mutation (ServiceAddressRequest). */
export interface ServiceAddressPayload {
  customerId: string
  addressLine1: string
  addressLine2: string
  city: string
  province: string
  postalCode: string
  countryCode: string
  isDefault: boolean
}

/** The body of an APPLIANCE_MODEL CREATE or UPDATE mutation (ApplianceModelRequest). */
export interface ApplianceModelPayload {
  applianceType: string
  brand: string
  modelNo: string
  /** The schematic and the service manual follow the model: every appliance of the model has the same ones. */
  schematicDocumentId: string | null
  /** Where the wiring diagram is on the appliance. */
  schematicLocation: string
  serviceManualDocumentId: string | null
}

/** The body of an APPLIANCE CREATE or UPDATE mutation (ApplianceRequest). */
export interface AppliancePayload {
  customerId: string
  applianceModelId: string
  serialNo: string
  serialVerified: boolean
  identityNotes: string
  customerAssetRef: string
  purchaseOrderNumber: string
  /** yyyy-MM-dd or null */
  productionDate: string | null
  estimatedAgeYears: number | null
}

/**
 * The body of a CASE CREATE or UPDATE mutation. The server lets the symptom fields change; the links cannot change.
 * On an update a missing classification field means "unchanged", so all three are always sent.
 */
export interface CasePayload {
  customerId: string
  serviceAddressId: string
  applianceId: string
  reportedSymptoms: string
  primarySymptom: PrimarySymptom | null
  secondarySymptoms: PrimarySymptom[]
  failureTiming: FailureTiming
  relatedCaseId: string | null
}

/** The body of a CASE_ERROR_CODE CREATE or UPDATE mutation. */
export interface CaseErrorCodePayload {
  diagnosticCaseId: string
  errorCode: string
  displayMessage: string
  codeStatus: ErrorCodeStatus
}

/** The body of a CASE_PART CREATE or UPDATE mutation (CasePartRequest). The case of a part never changes. */
export interface CasePartPayload {
  diagnosticCaseId: string
  partName: string
  oemPartNo: string
  quantity: number
  unit: string
  supplier: string
  partsSupplyStatus: PartsSupplyStatus
  /** yyyy-MM-dd or null */
  orderedOn: string | null
  expectedArrivalOn: string | null
  arrivedOn: string | null
  warrantyMonths: number | null
  installedVisitId: string | null
}

/** The body of a CASE TRANSITION mutation. */
export interface CaseTransitionPayload {
  toState: CaseStatus
  reason: string | null
  disposition: CaseDisposition | null
}

/** The body of a SERVICE_VISIT CREATE or UPDATE mutation. A new visit is always PLANNED (the server sets that). */
export interface VisitPayload {
  diagnosticCaseId: string
  technicianId: string
  /** Empty: the server decides (FIRST for the first visit, FOLLOW_UP after that). RECALL must be chosen. */
  visitKind: VisitKind | null
  scheduledAt: string | null
  notes: string
}

/** The body of a VISIT_SAFETY_CHECK CREATE or UPDATE mutation. The visit is the entityId of the mutation. */
export interface VisitSafetyCheckPayload {
  safetyChecks: SafetyChecks
  utilityBaseline: UtilityBaseline
  environmentBaseline: EnvironmentBaseline
}

/** The body of a DIAGNOSTIC_STEP CREATE or UPDATE mutation. On UPDATE every field is replaced as sent. */
export interface DiagnosticStepPayload {
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

/** The body of a VISIT_COMPLETION CREATE or UPDATE mutation. The three statuses are replaced as sent. */
export interface VisitCompletionPayload {
  serviceVisitId: string
  verificationChecks: VerificationChecks
  finalStatus: FinalStatus | null
  fixOutcome: FixOutcome | null
  googleReviewStatus: GoogleReviewStatus | null
}

/** The body of a CASE_SUMMARY CREATE or UPDATE mutation. An empty category list means "unchanged" on update. */
export interface CaseSummaryPayload {
  diagnosticCaseId: string
  diagnosticOutcome: DiagnosticOutcome | null
  repairStatus: RepairStatus | null
  customerDecision: CustomerDecision | null
  failureCategories: FailureCategory[]
  primaryFailureCategory: FailureCategory | null
  dataTags: string
  technicianSummary: string
}

/** The body of a CASE_INVOICE CREATE or UPDATE mutation. A missing amount or status means "unchanged" on update. */
export interface CaseInvoicePayload {
  diagnosticCaseId: string
  /** Only sent when the status changes. A new bill is always a draft. */
  invoiceStatus: InvoiceStatus | null
  issuedAt: string | null
  serviceCallFee: number
  labourFee: number
  partsFee: number
  discountAmount: number
  taxRate: number
}

/** The body of a CASE_INVOICE_PAYMENT CREATE or UPDATE mutation. */
export interface CaseInvoicePaymentPayload {
  caseInvoiceId: string
  amount: number
  paymentMethod: PaymentMethod
  referenceNo: string
  receivedAt: string
}

/** The body of a SERVICE_VISIT TRANSITION mutation. The reason of a pause or a cancel is sent as `notes`. */
export interface VisitTransitionPayload {
  toState: VisitStatus
  notes: string | null
}

// ---- Sync push (see sync-api-contract.md) ----

export type SyncEntityType =
  | 'CUSTOMER'
  | 'SERVICE_ADDRESS'
  | 'APPLIANCE_MODEL'
  | 'APPLIANCE'
  | 'CASE'
  | 'CASE_ERROR_CODE'
  | 'SERVICE_VISIT'
  | 'VISIT_SAFETY_CHECK'
  | 'DIAGNOSTIC_STEP'
  | 'VISIT_COMPLETION'
  | 'CASE_SUMMARY'
  | 'CASE_INVOICE'
  | 'CASE_INVOICE_PAYMENT'
  | 'ATTACHMENT'
  | 'APPLIANCE_DOCUMENT'
  | 'CASE_PART'

/** What can be downloaded. The state events and the list of users are only downloaded, never sent. */
export type PullEntityType = SyncEntityType | 'STATE_EVENT' | 'USER'

/** REGISTER announces a file (an attachment or an appliance document). The bytes are uploaded separately and never travel through sync. */
export type SyncOperation = 'CREATE' | 'UPDATE' | 'ARCHIVE' | 'TRANSITION' | 'REGISTER'

export type SyncResultStatus = 'ACCEPTED' | 'REJECTED' | 'BLOCKED'

export interface PushMutation {
  mutationId: string
  entityType: SyncEntityType
  entityId: string
  operation: SyncOperation
  rowVersion?: number
  clientRecordedAt: string
  dependsOn: string[]
  payload?: unknown
}

export interface PushRequest {
  outboxOwnerUserId: string
  mutations: PushMutation[]
}

export interface PushResult {
  mutationId: string
  status: SyncResultStatus
  replayed: boolean
  entityType: SyncEntityType
  entityId: string
  rowVersion?: number
  blockedBy?: string[]
  error?: ApiErrorBody
}

export interface PushResponse {
  serverTime: string
  results: PushResult[]
}
