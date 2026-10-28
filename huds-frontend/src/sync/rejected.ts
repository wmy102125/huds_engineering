import { db } from '../db/db'
import type { OutboxItem } from '../db/db'
import { clearLocalChange, deleteLocal } from './localRecords'
import { resetCursor } from './pull'

/** Changes the server refused. The user has to decide what to do with them. */
export function listRejected(): Promise<OutboxItem[]> {
  return db().outbox.where('status').equals('REJECTED').sortBy('seq')
}

/**
 * The user gives up a change that the server refused. The change leaves the outbox.
 * A record that never existed on the server disappears (with the changes that waited for it).
 * Any other record goes back to what the server has: the download starts from the beginning once,
 * because the server only sends records that changed, and this one did not.
 */
export async function discardRejected(item: OutboxItem): Promise<void> {
  const database = db()
  await database.transaction('rw', database.customers, database.addresses, database.models, database.appliances, database.outbox, async () => {
    await database.outbox.delete(item.seq)
    if (item.operation === 'CREATE') {
      await deleteLocal(item.entityType, item.entityId)
      // Changes that waited for this record (for example the addresses of a new customer) cannot succeed any more.
      const dependants = await database.outbox.filter((other) => other.dependsOn.includes(item.mutationId)).toArray()
      for (const other of dependants) {
        await database.outbox.delete(other.seq)
        await deleteLocal(other.entityType, other.entityId)
      }
    } else {
      await clearLocalChange(item.entityType, item.entityId)
    }
  })
  if (item.operation !== 'CREATE') {
    await resetCursor(item.entityType)
  }
}

/** A short text that tells the user which record a change was about. */
export function describeChange(item: OutboxItem): string {
  const payload = item.payload as
    | { customerName?: string; addressLine1?: string; brand?: string; modelNo?: string; serialNo?: string }
    | undefined
  const what: Record<string, string> = {
    CUSTOMER: 'Customer',
    SERVICE_ADDRESS: 'Address',
    APPLIANCE_MODEL: 'Appliance model',
    APPLIANCE: 'Appliance',
  }
  const label = what[item.entityType] ?? item.entityType
  const brandAndModel = [payload?.brand, payload?.modelNo].filter(Boolean).join(' ')
  const name = payload?.customerName || payload?.addressLine1 || brandAndModel || payload?.serialNo
  return name ? `${label} "${name}"` : `${label} (${item.operation.toLowerCase()})`
}
