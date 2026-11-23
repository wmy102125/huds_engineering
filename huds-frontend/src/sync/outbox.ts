import type { SyncEntityType, SyncOperation } from '../api/types'
import { db } from '../db/db'
import type { OutboxItem } from '../db/db'

/**
 * The changes in the outbox that belong to one record, oldest first.
 * The type is part of the key because the safety checks of a visit have the id of the visit.
 */
export function outboxOfRecord(entityType: SyncEntityType, entityId: string): Promise<OutboxItem[]> {
  return db()
    .outbox.where('entityId')
    .equals(entityId)
    .and((item) => item.entityType === entityType)
    .sortBy('seq')
}

/** The id of the newest change of a record that has not been sent yet, or undefined when there is none. */
export async function findLastUnsentMutationId(entityType: SyncEntityType, entityId: string): Promise<string | undefined> {
  const items = await outboxOfRecord(entityType, entityId)
  return items.filter((item) => item.status === 'PENDING' || item.status === 'BLOCKED').at(-1)?.mutationId
}

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
 * A TRANSITION is never merged with anything: every step of a case is a fact of its own and
 * the server writes one history entry for each. Such changes are sent one after the other
 * (the sync engine takes one change per record in every request).
 */
export async function enqueueMutation(input: NewMutation): Promise<EnqueueResult> {
  const database = db()
  const now = new Date().toISOString()

  const sameRecord = await outboxOfRecord(input.entityType, input.entityId)
  // The newest change that has not been sent yet. Most records have at most one; a case can have several.
  const unsent = sameRecord.filter((item) => item.status === 'PENDING' || item.status === 'BLOCKED').at(-1)

  if (unsent) {
    if (unsent.operation === 'ARCHIVE') {
      throw new Error('This record is already waiting to be archived.')
    }
    if (input.operation === 'CREATE') {
      throw new Error('This record already exists.')
    }
    if (input.operation === 'TRANSITION' || unsent.operation === 'TRANSITION') {
      // It is based on the version that the unsent change will create; the sync engine fills that in when the earlier change is accepted.
      await database.outbox.add({
        mutationId: crypto.randomUUID(),
        entityType: input.entityType,
        entityId: input.entityId,
        operation: input.operation,
        rowVersion: undefined,
        payload: input.payload,
        clientRecordedAt: now,
        dependsOn: input.dependsOn ?? [],
        status: 'PENDING',
      })
      return 'QUEUED'
    }
    if (unsent.operation === 'CREATE' && input.operation === 'ARCHIVE') {
      await database.outbox.delete(unsent.seq)
      return 'DISCARDED'
    }
    if (input.operation === 'UPDATE') {
      // The new content may refer to records that were created after the unsent change was queued (a document of a model),
      // so what the change waits for is the union of both.
      const dependsOn = [...new Set([...unsent.dependsOn, ...(input.dependsOn ?? [])])]
      await database.outbox.update(unsent.seq, { payload: input.payload, clientRecordedAt: now, status: 'PENDING', dependsOn })
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
 * Finds the change that makes a record exist on the server (a CREATE, or a REGISTER) and has not been accepted yet.
 * A change that needs this record (for example an address of a new customer) lists its id in `dependsOn`,
 * so the server runs them in order and does not run the second one if the first fails.
 */
export async function findCreateMutationId(entityId: string): Promise<string | undefined> {
  const items = await db().outbox.where('entityId').equals(entityId).toArray()
  return items.find((item) => item.operation === 'CREATE' || item.operation === 'REGISTER')?.mutationId
}
