import type { SyncEntityType, SyncOperation } from '../api/types'
import { db } from '../db/db'

export interface NewMutation {
  entityType: SyncEntityType
  entityId: string
  operation: SyncOperation
  /** The row version the change is based on. Required for UPDATE and ARCHIVE, unknown while the record is still waiting to be created. */
  rowVersion?: number
  payload?: unknown
  /** mutationIds of changes that must be processed first (for example the customer of a new address). */
  dependsOn?: string[]
}

/** QUEUED: a change is waiting in the outbox. DISCARDED: the change cancelled out a record the server never saw. */
export type EnqueueResult = 'QUEUED' | 'DISCARDED'

/**
 * Adds a change to the outbox. Call it inside the same Dexie transaction that writes the local record,
 * so the record and its change are saved together or not at all.
 *
 * A change that has not been sent yet is merged with the new one, because the server accepts only
 * one change per record in a request:
 *   CREATE + UPDATE  -> one CREATE with the latest content
 *   UPDATE + UPDATE  -> one UPDATE with the latest content
 *   CREATE + ARCHIVE -> nothing at all (the server never heard of the record)
 *   UPDATE + ARCHIVE -> one ARCHIVE
 */
export async function enqueueMutation(input: NewMutation): Promise<EnqueueResult> {
  const database = db()
  const now = new Date().toISOString()

  const sameRecord = await database.outbox.where('entityId').equals(input.entityId).toArray()
  const unsent = sameRecord.find((item) => item.status === 'PENDING' || item.status === 'BLOCKED')

  if (unsent) {
    if (unsent.operation === 'ARCHIVE') {
      throw new Error('This record is already waiting to be archived.')
    }
    if (input.operation === 'CREATE') {
      throw new Error('This record already exists.')
    }
    if (unsent.operation === 'CREATE' && input.operation === 'ARCHIVE') {
      await database.outbox.delete(unsent.seq)
      return 'DISCARDED'
    }
    if (input.operation === 'UPDATE') {
      await database.outbox.update(unsent.seq, { payload: input.payload, clientRecordedAt: now, status: 'PENDING' })
    } else {
      // The unsent change is an UPDATE and the new one is an ARCHIVE.
      await database.outbox.update(unsent.seq, {
        operation: 'ARCHIVE',
        payload: undefined,
        clientRecordedAt: now,
        status: 'PENDING',
      })
    }
    return 'QUEUED'
  }

  await database.outbox.add({
    mutationId: crypto.randomUUID(),
    entityType: input.entityType,
    entityId: input.entityId,
    operation: input.operation,
    rowVersion: input.rowVersion,
    payload: input.payload,
    clientRecordedAt: now,
    dependsOn: input.dependsOn ?? [],
    status: 'PENDING',
  })
  return 'QUEUED'
}

/** Number of changes that still have to reach the server (rejected ones are not counted). */
export function countWaiting(): Promise<number> {
  return db().outbox.where('status').notEqual('REJECTED').count()
}

/**
 * Finds the CREATE change of a record that the server has not accepted yet.
 * A change that needs this record (for example an address of a new customer) lists its id in `dependsOn`,
 * so the server runs them in order and does not run the second one if the first fails.
 */
export async function findCreateMutationId(entityId: string): Promise<string | undefined> {
  const items = await db().outbox.where('entityId').equals(entityId).toArray()
  return items.find((item) => item.operation === 'CREATE')?.mutationId
}
