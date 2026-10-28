import { useCallback, useState } from 'react'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalAddress, LocalCustomer } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import { navigate } from '../../routes/useRoute'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { AddressFormModal } from '../addresses/AddressFormModal'
import { archiveAddress, listAddresses } from '../addresses/addressStore'
import { CustomerFormModal } from './CustomerFormModal'
import { archiveCustomer, getCustomer } from './customerStore'

interface Props {
  customerId: string
  sync: SyncStatus
}

interface Loaded {
  customer: LocalCustomer | undefined
  addresses: LocalAddress[]
}

/** One customer on top, the customer's service addresses below. */
export function CustomerDetailPage({ customerId, sync }: Props) {
  const load = useCallback(
    async (): Promise<Loaded> => ({ customer: await getCustomer(customerId), addresses: await listAddresses(customerId) }),
    [customerId],
  )
  const { data, reload } = useLocalQuery(load, sync.tick)
  const [editingCustomer, setEditingCustomer] = useState(false)
  // "new" opens an empty form; an address opens the form for it; null keeps the dialog closed.
  const [addressForm, setAddressForm] = useState<LocalAddress | 'new' | null>(null)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const { customer, addresses } = data
  if (!customer || customer.archiving) {
    return (
      <section>
        <a className="back" href="#/my/customers">
          ← Customers
        </a>
        <p className="muted">This customer was not found on this device.</p>
      </section>
    )
  }

  const saved = () => {
    void reload()
    void sync.requestSync()
  }

  const handleArchiveCustomer = async () => {
    if (!window.confirm(`Archive ${customer.customerName}? The customer's addresses are archived too.`)) {
      return
    }
    try {
      await archiveCustomer(customer)
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'The customer could not be archived.')
      return
    }
    void sync.requestSync()
    navigate('/my/customers')
  }

  const handleArchiveAddress = async (address: LocalAddress) => {
    if (!window.confirm(`Archive ${address.addressLine1}?`)) {
      return
    }
    try {
      await archiveAddress(address)
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'The address could not be archived.')
      return
    }
    saved()
  }

  return (
    <section>
      <a className="back" href="#/my/customers">
        ← Customers
      </a>
      <div className="page-head">
        <h2>{customer.customerName}</h2>
        <div className="row">
          <button type="button" onClick={() => setEditingCustomer(true)}>
            Edit
          </button>
          <button type="button" onClick={() => void handleArchiveCustomer()}>
            Archive
          </button>
        </div>
      </div>

      <div className="card">
        <dl className="facts">
          <dt>Phone</dt>
          <dd>{customer.phone || '-'}</dd>
          <dt>Email</dt>
          <dd>{customer.email || '-'}</dd>
          <dt>Notes</dt>
          <dd>{customer.notes || '-'}</dd>
          <dt>Status</dt>
          <dd>
            <SyncBadge state={customer.syncState} />
          </dd>
        </dl>
        <AuditLine record={customer} />
      </div>

      <div className="page-head">
        <h3>Service addresses</h3>
        <button type="button" className="primary" onClick={() => setAddressForm('new')}>
          + New address
        </button>
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
                  <button type="button" onClick={() => setAddressForm(address)}>
                    Edit
                  </button>
                  <button type="button" onClick={() => void handleArchiveAddress(address)}>
                    Archive
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editingCustomer && (
        <CustomerFormModal
          customer={customer}
          onClose={() => setEditingCustomer(false)}
          onSaved={() => {
            setEditingCustomer(false)
            saved()
          }}
        />
      )}
      {addressForm !== null && (
        <AddressFormModal
          customerId={customerId}
          address={addressForm === 'new' ? undefined : addressForm}
          onClose={() => setAddressForm(null)}
          onSaved={() => {
            setAddressForm(null)
            saved()
          }}
        />
      )}
    </section>
  )
}
