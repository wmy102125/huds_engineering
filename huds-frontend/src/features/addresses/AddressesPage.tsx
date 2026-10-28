import { useCallback, useEffect, useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { LocalAddress, LocalCustomer } from '../../db/db'
import { listCustomers } from '../customers/customerStore'
import { Modal } from '../my/Modal'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { archiveAddress, createAddress, listAddresses, updateAddress } from './addressStore'
import type { AddressForm } from './addressStore'

interface Props {
  sync: SyncStatus
}

const EMPTY_FORM: AddressForm = {
  addressLine1: '',
  addressLine2: '',
  city: 'Sydney',
  province: 'NS',
  postalCode: '',
  countryCode: 'CA',
  isDefault: false,
}

export function AddressesPage({ sync }: Props) {
  const [customers, setCustomers] = useState<LocalCustomer[]>([])
  const [addresses, setAddresses] = useState<LocalAddress[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState<AddressForm>(EMPTY_FORM)
  const [editing, setEditing] = useState<LocalAddress | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)

  // Until the user picks a customer, the first one in the list is used.
  const customerId = selectedId ?? customers[0]?.customerId ?? null

  const reload = useCallback(async () => {
    const loadedCustomers = await listCustomers()
    setCustomers(loadedCustomers)
    const current = selectedId ?? loadedCustomers[0]?.customerId ?? null
    setAddresses(current ? await listAddresses(current) : [])
  }, [selectedId])

  useEffect(() => {
    // Started from a timer, not from the effect body, so the first render is not repeated at once.
    const timer = window.setTimeout(() => void reload(), 0)
    return () => window.clearTimeout(timer)
  }, [reload, sync.tick])

  const { requestSync } = sync

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setFormError(null)
    if (!customerId) {
      return
    }
    const clean: AddressForm = {
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim(),
      city: form.city.trim(),
      province: form.province.trim(),
      postalCode: form.postalCode.trim(),
      countryCode: form.countryCode.trim().toUpperCase(),
      isDefault: form.isDefault,
    }
    if (!clean.addressLine1 || !clean.city || !clean.province || !clean.postalCode || !clean.countryCode) {
      setFormError('Please fill in the street, city, province, postal code and country code.')
      return
    }
    try {
      if (editing) {
        await updateAddress(editing, clean)
      } else {
        await createAddress(customerId, clean)
      }
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'The address could not be saved.')
      return
    }
    setForm(EMPTY_FORM)
    setEditing(null)
    setFormOpen(false)
    await reload()
    void requestSync()
  }

  const startEdit = (address: LocalAddress) => {
    setEditing(address)
    setForm({
      addressLine1: address.addressLine1,
      addressLine2: address.addressLine2,
      city: address.city,
      province: address.province,
      postalCode: address.postalCode,
      countryCode: address.countryCode,
      isDefault: address.isDefault,
    })
    setFormError(null)
    setFormOpen(true)
  }

  const startNew = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setFormOpen(true)
  }

  const cancelEdit = () => {
    setFormOpen(false)
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  const handleArchive = async (address: LocalAddress) => {
    if (!window.confirm(`Archive ${address.addressLine1}?`)) {
      return
    }
    try {
      await archiveAddress(address)
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'The address could not be archived.')
      return
    }
    await reload()
    void requestSync()
  }

  if (customers.length === 0) {
    return (
      <section>
        <h2>Service addresses</h2>
        <p className="muted">Add a customer first. An address always belongs to a customer.</p>
      </section>
    )
  }

  // The current default address cannot be un-defaulted; make another address the default instead.
  const lockDefault = editing?.isDefault === true

  return (
    <section>
      <div className="page-head">
        <h2>Service addresses</h2>
        <div className="row">
          <select
            className="search"
            aria-label="Customer"
            value={customerId ?? ''}
            onChange={(event) => {
              setSelectedId(event.target.value)
              cancelEdit()
            }}
          >
            {customers.map((customer) => (
              <option key={customer.customerId} value={customer.customerId}>
                {customer.customerName}
              </option>
            ))}
          </select>
          <button type="button" className="primary" onClick={startNew}>
            + New address
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Address</th>
              <th>City</th>
              <th>Province</th>
              <th>Postal code</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {addresses.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  No addresses yet.
                </td>
              </tr>
            )}
            {addresses.map((address) => (
              <tr key={address.serviceAddressId}>
                <td data-label="Address">
                  <strong>
                    {address.addressLine1}
                    {address.addressLine2 && `, ${address.addressLine2}`}
                  </strong>{' '}
                  {address.isDefault && <span className="badge wait">Default</span>}
                </td>
                <td data-label="City">{address.city}</td>
                <td data-label="Province">{address.province}</td>
                <td data-label="Postal code">{address.postalCode}</td>
                <td data-label="Status">
                  <SyncBadge state={address.syncState} />
                </td>
                <td className="actions">
                  <button type="button" onClick={() => startEdit(address)}>
                    Edit
                  </button>
                  <button type="button" onClick={() => void handleArchive(address)}>
                    Archive
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {formOpen && (
        <Modal title={editing ? 'Edit address' : 'New address'} onClose={cancelEdit}>
          <form className="stack" onSubmit={handleSubmit}>
            <label>
              Street address
              <input
                value={form.addressLine1}
                onChange={(event) => setForm({ ...form, addressLine1: event.target.value })}
                autoComplete="off"
                autoFocus
              />
            </label>
            <label>
              Unit / apartment (optional)
              <input
                value={form.addressLine2}
                onChange={(event) => setForm({ ...form, addressLine2: event.target.value })}
                autoComplete="off"
              />
            </label>
            <div className="row">
              <label className="grow">
                City
                <input value={form.city} onChange={(event) => setForm({ ...form, city: event.target.value })} />
              </label>
              <label className="narrow">
                Province
                <input value={form.province} onChange={(event) => setForm({ ...form, province: event.target.value })} />
              </label>
            </div>
            <div className="row">
              <label className="grow">
                Postal code
                <input
                  value={form.postalCode}
                  onChange={(event) => setForm({ ...form, postalCode: event.target.value })}
                />
              </label>
              <label className="narrow">
                Country
                <input
                  value={form.countryCode}
                  onChange={(event) => setForm({ ...form, countryCode: event.target.value })}
                  maxLength={2}
                />
              </label>
            </div>
            <label className="check">
              <input
                type="checkbox"
                checked={form.isDefault || lockDefault}
                disabled={lockDefault}
                onChange={(event) => setForm({ ...form, isDefault: event.target.checked })}
              />
              Default address of this customer
            </label>
            {formError && <p className="error">{formError}</p>}
            <div className="row end">
              <button type="button" onClick={cancelEdit}>
                Cancel
              </button>
              <button type="submit" className="primary">
                {editing ? 'Save changes' : 'Add address'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  )
}

function SyncBadge({ state }: { state: LocalAddress['syncState'] }) {
  if (state === 'SYNCED') {
    return <span className="badge ok">Synced</span>
  }
  if (state === 'PENDING') {
    return <span className="badge wait">Saved on this device</span>
  }
  return <span className="badge bad">Refused</span>
}
