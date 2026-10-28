import { useState } from 'react'
import { Pager, PAGE_SIZE } from '../../components/Pager'
import { SyncBadge } from '../../components/SyncBadge'
import { useLocalQuery } from '../../db/useLocalQuery'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { CustomerFormModal } from './CustomerFormModal'
import { listCustomers } from './customerStore'

interface Props {
  sync: SyncStatus
}

export function CustomersPage({ sync }: Props) {
  const { data, reload } = useLocalQuery(listCustomers, sync.tick)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)

  const customers = data ?? []
  const needle = query.trim().toLowerCase()
  const matches = customers.filter(
    (customer) =>
      needle === '' ||
      customer.customerName.toLowerCase().includes(needle) ||
      customer.email.toLowerCase().includes(needle) ||
      customer.phone.toLowerCase().includes(needle),
  )
  const shown = matches.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <section>
      <div className="page-head">
        <h2>Customers</h2>
        <div className="row">
          <input
            className="search"
            type="search"
            placeholder="Search name, email or phone"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
          />
          <button type="button" className="primary" onClick={() => setFormOpen(true)}>
            + New customer
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Phone</th>
              <th>Email</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {data !== undefined && shown.length === 0 && (
              <tr>
                <td colSpan={4} className="muted">
                  {customers.length === 0 ? 'No customers yet.' : 'No customer matches the search.'}
                </td>
              </tr>
            )}
            {shown.map((customer) => (
              <tr key={customer.customerId}>
                <td data-label="Name">
                  <a href={`#/my/customers/${customer.customerId}`}>
                    <strong>{customer.customerName}</strong>
                  </a>
                </td>
                <td data-label="Phone">{customer.phone}</td>
                <td data-label="Email">{customer.email}</td>
                <td data-label="Status">
                  <SyncBadge state={customer.syncState} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} total={matches.length} onPage={setPage} />

      {formOpen && (
        <CustomerFormModal
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false)
            void reload()
            void sync.requestSync()
          }}
        />
      )}
    </section>
  )
}
