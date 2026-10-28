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
  }
}
