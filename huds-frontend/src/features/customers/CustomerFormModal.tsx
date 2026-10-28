import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CustomerPayload } from '../../api/types'
import type { LocalCustomer } from '../../db/db'
import { Modal } from '../my/Modal'
import { createCustomer, updateCustomer } from './customerStore'

interface Props {
  /** The customer to edit, or nothing to create a new one. */
  customer?: LocalCustomer
  onClose: () => void
  onSaved: () => void
}

export function CustomerFormModal({ customer, onClose, onSaved }: Props) {
  const [form, setForm] = useState<CustomerPayload>({
    customerName: customer?.customerName ?? '',
    email: customer?.email ?? '',
    phone: customer?.phone ?? '',
    notes: customer?.notes ?? '',
  })
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    const clean: CustomerPayload = {
      customerName: form.customerName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      notes: form.notes.trim(),
    }
    if (clean.customerName === '' || !clean.email.includes('@')) {
      setError('Please enter a name and a valid email address.')
      return
    }
    try {
      if (customer) {
        await updateCustomer(customer, clean)
      } else {
        await createCustomer(clean)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The customer could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={customer ? 'Edit customer' : 'New customer'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Name
          <input
            value={form.customerName}
            onChange={(event) => setForm({ ...form, customerName: event.target.value })}
            autoComplete="off"
            autoFocus
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            autoComplete="off"
          />
        </label>
        <label>
          Phone
          <input
            type="tel"
            value={form.phone}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
            autoComplete="off"
          />
        </label>
        <label>
          Notes
          <textarea value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} rows={3} />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            {customer ? 'Save changes' : 'Add customer'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
