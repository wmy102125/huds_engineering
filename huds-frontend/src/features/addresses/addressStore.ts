import type { ServiceAddressPayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalAddress } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'

/** Same pattern as the customer store: the local record and the outbox change are saved in one transaction. */

export async function listAddresses(customerId: string): Promise<LocalAddress[]> {
  const all = await db().addresses.where('customerId').equals(customerId).toArray()
  return all.filter((address) => !address.archiving).sort((a, b) => a.addressLine1.localeCompare(b.addressLine1))
}

export type AddressForm = Omit<ServiceAddressPayload, 'customerId'>

export async function createAddress(customerId: string, form: AddressForm): Promise<void> {
  const database = db()
  const serviceAddressId = crypto.randomUUID()
  await database.transaction('rw', database.addresses, database.outbox, async () => {
    const existing = await database.addresses.where('customerId').equals(customerId).toArray()
    // The server makes the first address of a customer the default one; show the same here.
    const isDefault = form.isDefault || existing.length === 0
    const payload: ServiceAddressPayload = { customerId, ...form, isDefault }
    if (isDefault) {
      await clearDefault(customerId)
    }
    await database.addresses.add({ serviceAddressId, ...payload, syncState: 'PENDING' })
    // If the customer itself is not on the server yet, the address has to wait for it.
    const customerCreate = await findCreateMutationId(customerId)
    await enqueueMutation({
      entityType: 'SERVICE_ADDRESS',
      entityId: serviceAddressId,
      operation: 'CREATE',
      payload,
      dependsOn: customerCreate ? [customerCreate] : [],
    })
  })
}

export async function updateAddress(address: LocalAddress, form: AddressForm): Promise<void> {
  const database = db()
  await database.transaction('rw', database.addresses, database.outbox, async () => {
    const payload: ServiceAddressPayload = { customerId: address.customerId, ...form }
    if (payload.isDefault) {
      await clearDefault(address.customerId)
    }
    await database.addresses.update(address.serviceAddressId, { ...form, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'SERVICE_ADDRESS',
      entityId: address.serviceAddressId,
      operation: 'UPDATE',
      rowVersion: address.rowVersion,
      payload,
    })
  })
}

export async function archiveAddress(address: LocalAddress): Promise<void> {
  const database = db()
  await database.transaction('rw', database.addresses, database.outbox, async () => {
    const result = await enqueueMutation({
      entityType: 'SERVICE_ADDRESS',
      entityId: address.serviceAddressId,
      operation: 'ARCHIVE',
      rowVersion: address.rowVersion,
    })
    if (result === 'DISCARDED') {
      await database.addresses.delete(address.serviceAddressId)
    } else {
      await database.addresses.update(address.serviceAddressId, { syncState: 'PENDING', archiving: true })
    }
  })
}

/** A customer has one default address. Show the change at once; the next download confirms it. */
async function clearDefault(customerId: string): Promise<void> {
  const addresses = await db().addresses.where('customerId').equals(customerId).toArray()
  for (const address of addresses) {
    if (address.isDefault) {
      await db().addresses.update(address.serviceAddressId, { isDefault: false })
    }
  }
}
