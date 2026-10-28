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

export interface CustomerChange extends PulledAudit {
  customerId: string
  customerName: string
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
}

export interface ApplianceChange extends PulledAudit {
  applianceId: string
  applianceModelId: string
  serialNo: string | null
  serialVerified: boolean
  identityNotes: string | null
}

export interface PullResponse<T> {
  items: T[]
  nextSince: string
  nextSinceId: string
  hasMore: boolean
}

/** The body of a CUSTOMER CREATE or UPDATE mutation (CustomerRequest). */
export interface CustomerPayload {
  customerName: string
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
}

/** The body of an APPLIANCE CREATE or UPDATE mutation (ApplianceRequest). */
export interface AppliancePayload {
  applianceModelId: string
  serialNo: string
  serialVerified: boolean
  identityNotes: string
}

// ---- Sync push (see sync-api-contract.md) ----

export type SyncEntityType = 'CUSTOMER' | 'SERVICE_ADDRESS' | 'APPLIANCE_MODEL' | 'APPLIANCE'

export type SyncOperation = 'CREATE' | 'UPDATE' | 'ARCHIVE'

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
