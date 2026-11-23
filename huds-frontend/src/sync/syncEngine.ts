import { apiRequest, ApiError } from '../api/http'
import type { PushMutation, PushRequest, PushResponse, PushResult } from '../api/types'
import { db } from '../db/db'
import type { OutboxItem } from '../db/db'
import { applyAcceptedLocal, markSyncState } from './localRecords'
import { outboxOfRecord } from './outbox'

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
 * The server tells records apart by id alone, so two records of different types that share an id (a visit and its
 * safety checks) cannot be in the same request either; the later one is sent in the next round.
 */
async function pickBatch(): Promise<OutboxItem[]> {
  const waiting = await db().outbox.where('status').anyOf('PENDING', 'BLOCKED').sortBy('seq')
  const refused = await db().outbox.where('status').equals('REJECTED').toArray()
  // The key has the type in it: the safety checks of a visit have the id of the visit.
  const recordKey = (item: OutboxItem) => `${item.entityType}:${item.entityId}`
  const refusedRecords = new Set(refused.map(recordKey))
  const seenRecords = new Set<string>()
  const idsInBatch = new Set<string>()
  const candidates: OutboxItem[] = []
  for (const item of waiting) {
    if (seenRecords.has(recordKey(item))) {
      continue
    }
    seenRecords.add(recordKey(item))
    // A later change was made on top of the refused one and would fail as well. It waits until the user has decided about the refused change.
    if (refusedRecords.has(recordKey(item))) {
      continue
    }
    // UPDATE and ARCHIVE must say which version they are based on. It is not known yet
    // while the record's CREATE has not been accepted. A REGISTER creates a record, so it has no version either.
    if (item.operation !== 'CREATE' && item.operation !== 'REGISTER' && item.rowVersion === undefined) {
      continue
    }
    // Another record with the same id is already in this request.
    if (idsInBatch.has(item.entityId)) {
      continue
    }
    idsInBatch.add(item.entityId)
    candidates.push(item)
  }
  return (await orderByDependencies(candidates)).slice(0, MAX_BATCH)
}

/**
 * Puts every change after the changes it depends on. The queue order is the order in which changes were made, but a change can
 * come to depend on one queued after it (a model that was created offline and gets a schematic afterwards). The server needs
 * the dependency earlier in the request or already processed, so a change whose dependency is still waiting somewhere else
 * (another record's later change, or a refused one) is left for a later round.
 */
async function orderByDependencies(candidates: OutboxItem[]): Promise<OutboxItem[]> {
  const stillQueued = new Set((await db().outbox.toArray()).map((item) => item.mutationId))
  const ordered: OutboxItem[] = []
  const placed = new Set<string>()
  let progress = true
  while (progress) {
    progress = false
    for (const item of candidates) {
      if (placed.has(item.mutationId)) {
        continue
      }
      // A dependency that left the queue was accepted already.
      if (item.dependsOn.every((id) => !stillQueued.has(id) || placed.has(id))) {
        ordered.push(item)
        placed.add(item.mutationId)
        progress = true
      }
    }
  }
  return ordered
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
  const tables = [
    database.outbox,
    database.customers,
    database.addresses,
    database.models,
    database.appliances,
    database.cases,
    database.caseErrorCodes,
    database.caseParts,
    database.visits,
    database.visitSafetyChecks,
    database.diagnosticSteps,
    database.visitCompletions,
    database.caseSummaries,
    database.caseInvoices,
    database.caseInvoicePayments,
    database.attachments,
    database.applianceDocuments,
  ]
  await database.transaction('rw', tables, async () => {
    await database.outbox.delete(item.seq)

    // Later changes of the same record were waiting for this version number.
    const later = await outboxOfRecord(item.entityType, item.entityId)
    for (const other of later) {
      if (other.status !== 'REJECTED' && other.operation !== 'CREATE' && result.rowVersion !== undefined) {
        await database.outbox.update(other.seq, { rowVersion: result.rowVersion })
      }
    }

    const stillWaiting = later.some((other) => other.status !== 'REJECTED')
    await applyAcceptedLocal(item.entityType, item.entityId, item.operation, result.rowVersion, stillWaiting)
  })
}
