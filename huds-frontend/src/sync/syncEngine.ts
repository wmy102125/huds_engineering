import { apiRequest, ApiError } from '../api/http'
import type { PushMutation, PushRequest, PushResponse, PushResult } from '../api/types'
import { db } from '../db/db'
import type { OutboxItem } from '../db/db'
import { applyAcceptedLocal, markSyncState } from './localRecords'

const MAX_BATCH = 100
const MAX_ROUNDS = 10

export interface SyncOutcome {
  accepted: number
  rejected: number
  blocked: number
}

let running = false

/**
 * Sends everything that waits in the outbox to the server.
 * Only one run happens at a time; a second call returns at once.
 * Throws an ApiError when the server cannot be reached or refuses the whole request;
 * the changes then stay in the outbox and are sent again next time.
 */
export async function syncNow(userId: string): Promise<SyncOutcome> {
  const outcome: SyncOutcome = { accepted: 0, rejected: 0, blocked: 0 }
  if (running) {
    return outcome
  }
  running = true
  try {
    // A previous run may have been interrupted (tab closed). Sending again is safe:
    // the server recognises the same mutationId and returns the stored result.
    const interrupted = await db().outbox.where('status').equals('SENDING').toArray()
    for (const item of interrupted) {
      await db().outbox.update(item.seq, { status: 'PENDING' })
    }

    for (let round = 0; round < MAX_ROUNDS; round++) {
      const batch = await pickBatch()
      if (batch.length === 0) {
        break
      }
      const roundOutcome = await sendBatch(userId, batch)
      outcome.accepted += roundOutcome.accepted
      outcome.rejected += roundOutcome.rejected
      outcome.blocked += roundOutcome.blocked
      // Changes that had to wait (second change of the same record) can go out after an accepted one.
      if (roundOutcome.accepted === 0) {
        break
      }
    }
    return outcome
  } finally {
    running = false
  }
}

/**
 * Takes the oldest waiting change of every record. The server accepts only one change per record
 * in a request, and a later change may need the row version that comes back from the earlier one.
 */
async function pickBatch(): Promise<OutboxItem[]> {
  const waiting = await db().outbox.where('status').anyOf('PENDING', 'BLOCKED').sortBy('seq')
  const seenRecords = new Set<string>()
  const batch: OutboxItem[] = []
  for (const item of waiting) {
    if (seenRecords.has(item.entityId)) {
      continue
    }
    seenRecords.add(item.entityId)
    // UPDATE and ARCHIVE must say which version they are based on. It is not known yet
    // while the record's CREATE has not been accepted.
    if (item.operation !== 'CREATE' && item.rowVersion === undefined) {
      continue
    }
    batch.push(item)
    if (batch.length === MAX_BATCH) {
      break
    }
  }
  return batch
}

function toPushMutation(item: OutboxItem): PushMutation {
  return {
    mutationId: item.mutationId,
    entityType: item.entityType,
    entityId: item.entityId,
    operation: item.operation,
    rowVersion: item.rowVersion,
    clientRecordedAt: item.clientRecordedAt,
    dependsOn: item.dependsOn,
    payload: item.payload,
  }
}

async function sendBatch(userId: string, batch: OutboxItem[]): Promise<SyncOutcome> {
  const outcome: SyncOutcome = { accepted: 0, rejected: 0, blocked: 0 }

  for (const item of batch) {
    await db().outbox.update(item.seq, { status: 'SENDING' })
  }

  let response: PushResponse
  try {
    const request: PushRequest = { outboxOwnerUserId: userId, mutations: batch.map(toPushMutation) }
    response = await apiRequest<PushResponse>('/api/v1/sync/push', { method: 'POST', body: request })
  } catch (error) {
    // Nothing was processed (or we never learned the answer). Put the changes back.
    for (const item of batch) {
      await db().outbox.update(item.seq, { status: 'PENDING' })
    }
    throw error instanceof ApiError ? error : new ApiError(0, 'NETWORK', 'The sync request failed.', true, null)
  }

  const resultsById = new Map<string, PushResult>()
  for (const result of response.results) {
    resultsById.set(result.mutationId, result)
  }

  for (const item of batch) {
    const result = resultsById.get(item.mutationId)
    if (!result) {
      await db().outbox.update(item.seq, { status: 'PENDING' })
      continue
    }
    if (result.status === 'ACCEPTED') {
      await applyAccepted(item, result)
      outcome.accepted++
    } else if (result.status === 'REJECTED') {
      await db().outbox.update(item.seq, { status: 'REJECTED', error: result.error })
      await markSyncState(item.entityType, item.entityId, 'REJECTED')
      outcome.rejected++
    } else {
      await db().outbox.update(item.seq, { status: 'BLOCKED' })
      outcome.blocked++
    }
  }
  return outcome
}

/** The server accepted a change: remove it from the outbox and bring the local record up to date. */
async function applyAccepted(item: OutboxItem, result: PushResult): Promise<void> {
  const database = db()
  await database.transaction('rw', database.outbox, database.customers, database.addresses, database.models, database.appliances, async () => {
    await database.outbox.delete(item.seq)

    // Later changes of the same record were waiting for this version number.
    const later = await database.outbox.where('entityId').equals(item.entityId).toArray()
    for (const other of later) {
      if (other.status !== 'REJECTED' && other.operation !== 'CREATE' && result.rowVersion !== undefined) {
        await database.outbox.update(other.seq, { rowVersion: result.rowVersion })
      }
    }

    const stillWaiting = later.some((other) => other.status !== 'REJECTED')
    await applyAcceptedLocal(item.entityType, item.entityId, item.operation, result.rowVersion, stillWaiting)
  })
}
