import { apiRequest } from '../api/http'
import type {
  ApplianceChange,
  ApplianceModelChange,
  CustomerChange,
  PullResponse,
  ServiceAddressChange,
  SyncEntityType,
} from '../api/types'
import { db } from '../db/db'
import type { AuditFields } from '../db/db'

/**
 * Downloads what changed on the server since the last download, one entity type after the other.
 * The server returns changes oldest first, in batches. A record that was archived comes with archived=true
 * and is removed from this device.
 */

const PULL_TYPES: SyncEntityType[] = ['CUSTOMER', 'SERVICE_ADDRESS', 'APPLIANCE_MODEL', 'APPLIANCE']

/** Every download starts a little before the stored cursor, so a row that was committed late is not missed. */
const OVERLAP_MS = 5_000
const MAX_BATCHES = 200
const FIRST_ID = '00000000-0000-0000-0000-000000000000'

function cursorKey(entityType: SyncEntityType): string {
  return `cursor:${entityType}`
}

export async function pullAll(): Promise<void> {
  for (const entityType of PULL_TYPES) {
    await pullType(entityType)
  }
}

/** Forgets where the download stopped, so the next download starts from the beginning and brings the server's version of every record. */
export async function resetCursor(entityType: SyncEntityType): Promise<void> {
  await db().meta.delete(cursorKey(entityType))
}

async function pullType(entityType: SyncEntityType): Promise<void> {
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

async function applyBatch(entityType: SyncEntityType, items: unknown[]): Promise<void> {
  switch (entityType) {
    case 'CUSTOMER':
      await applyCustomers(items as CustomerChange[])
      break
    case 'SERVICE_ADDRESS':
      await applyAddresses(items as ServiceAddressChange[])
      break
    case 'APPLIANCE_MODEL':
      await applyModels(items as ApplianceModelChange[])
      break
    case 'APPLIANCE':
      await applyAppliances(items as ApplianceChange[])
      break
  }
}

function auditOf(item: ApplianceChange | ApplianceModelChange | CustomerChange | ServiceAddressChange): AuditFields {
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
        applianceModelId: item.applianceModelId,
        serialNo: item.serialNo ?? '',
        serialVerified: item.serialVerified,
        identityNotes: item.identityNotes ?? '',
        rowVersion: item.rowVersion,
        syncState: 'SYNCED',
        ...auditOf(item),
      })
    }
  })
}
