import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { LocalAddress } from '../../db/db'
import { Modal } from '../my/Modal'
import { createAddress, updateAddress } from './addressStore'
import type { AddressForm } from './addressStore'

interface Props {
  /** The customer the address belongs to. */
  customerId: string
  /** The address to edit, or nothing to create a new one. */
  address?: LocalAddress
  onClose: () => void
  onSaved: () => void
}

export function AddressFormModal({ customerId, address, onClose, onSaved }: Props) {
  const [form, setForm] = useState<AddressForm>({
    addressLine1: address?.addressLine1 ?? '',
    addressLine2: address?.addressLine2 ?? '',
    city: address?.city ?? 'Sydney',
    province: address?.province ?? 'NS',
    postalCode: address?.postalCode ?? '',
    countryCode: address?.countryCode ?? 'CA',
    isDefault: address?.isDefault ?? false,
  })
  const [error, setError] = useState<string | null>(null)

  // The current default address cannot be un-defaulted; make another address the default instead.
  const lockDefault = address?.isDefault === true

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
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
      setError('Please fill in the street, city, province, postal code and country code.')
      return
    }
    try {
      if (address) {
        await updateAddress(address, clean)
      } else {
        await createAddress(customerId, clean)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The address could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={address ? 'Edit address' : 'New address'} onClose={onClose}>
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
            <input value={form.postalCode} onChange={(event) => setForm({ ...form, postalCode: event.target.value })} />
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
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            {address ? 'Save changes' : 'Add address'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
