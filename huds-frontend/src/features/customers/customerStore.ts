import type { CustomerPayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalCustomer } from '../../db/db'
import { enqueueMutation } from '../../sync/outbox'

/**
 * Every change is written in two places inside ONE local transaction: the local customer
 * (so the screen shows it at once) and the outbox (so it is sent to the server later).
 */

export async function listCustomers(): Promise<LocalCustomer[]> {
  const all = await db().customers.toArray()
  return all.filter((customer) => !customer.archiving).sort((a, b) => a.customerName.localeCompare(b.customerName))
}

export function getCustomer(customerId: string): Promise<LocalCustomer | undefined> {
  return db().customers.get(customerId)
}

export async function createCustomer(form: CustomerPayload): Promise<void> {
  const database = db()
  // The id is made here, on the device, because the server cannot be asked while offline.
  const customerId = crypto.randomUUID()
  await database.transaction('rw', database.customers, database.outbox, async () => {
    await database.customers.add({ customerId, ...form, syncState: 'PENDING' })
    await enqueueMutation({ entityType: 'CUSTOMER', entityId: customerId, operation: 'CREATE', payload: form })
  })
}

export async function updateCustomer(customer: LocalCustomer, form: CustomerPayload): Promise<void> {
  const database = db()
  await database.transaction('rw', database.customers, database.outbox, async () => {
    await database.customers.update(customer.customerId, { ...form, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'CUSTOMER',
      entityId: customer.customerId,
      operation: 'UPDATE',
      rowVersion: customer.rowVersion,
      payload: form,
    })
  })
}

export async function archiveCustomer(customer: LocalCustomer): Promise<void> {
  const database = db()
  await database.transaction('rw', database.customers, database.addresses, database.outbox, async () => {
    const result = await enqueueMutation({
      entityType: 'CUSTOMER',
      entityId: customer.customerId,
      operation: 'ARCHIVE',
      rowVersion: customer.rowVersion,
    })
    if (result === 'DISCARDED') {
      // The server never saw this customer, so there is nothing to archive there.
      // Addresses that were added to it in the meantime never existed on the server either.
      await database.customers.delete(customer.customerId)
      const addresses = await database.addresses.where('customerId').equals(customer.customerId).toArray()
      for (const address of addresses) {
        await database.addresses.delete(address.serviceAddressId)
        const queued = await database.outbox.where('entityId').equals(address.serviceAddressId).toArray()
        for (const item of queued) {
          await database.outbox.delete(item.seq)
        }
      }
    } else {
      await database.customers.update(customer.customerId, { syncState: 'PENDING', archiving: true })
    }
  })
}
