import Dexie from 'dexie'
import type { EntityTable } from 'dexie'
import type { ApiErrorBody, SyncEntityType, SyncOperation } from '../api/types'

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
}

/** The local copy of an appliance (one physical unit of a model). */
export interface LocalAppliance extends LocalRecordBase {
  applianceId: string
  applianceModelId: string
  serialNo: string
  serialVerified: boolean
  identityNotes: string
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
