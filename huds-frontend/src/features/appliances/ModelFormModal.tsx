import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { ApplianceModelPayload } from '../../api/types'
import type { LocalModel } from '../../db/db'
import { Modal } from '../my/Modal'
import { APPLIANCE_TYPES } from './applianceTypes'
import { createModel, updateModel } from './modelStore'

interface Props {
  /** The model to edit, or nothing to create a new one. */
  model?: LocalModel
  onClose: () => void
  onSaved: () => void
}

export function ModelFormModal({ model, onClose, onSaved }: Props) {
  const [form, setForm] = useState<ApplianceModelPayload>({
    applianceType: model?.applianceType ?? 'WASHER',
    brand: model?.brand ?? '',
    modelNo: model?.modelNo ?? '',
  })
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    const clean: ApplianceModelPayload = {
      applianceType: form.applianceType,
      brand: form.brand.trim(),
      modelNo: form.modelNo.trim(),
    }
    try {
      if (model) {
        await updateModel(model, clean)
      } else {
        await createModel(clean)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The model could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={model ? 'Edit model' : 'New model'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Type
          <select value={form.applianceType} onChange={(event) => setForm({ ...form, applianceType: event.target.value })}>
            {APPLIANCE_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Brand
          <input value={form.brand} onChange={(event) => setForm({ ...form, brand: event.target.value })} autoComplete="off" autoFocus />
        </label>
        <label>
          Model number
          <input value={form.modelNo} onChange={(event) => setForm({ ...form, modelNo: event.target.value })} autoComplete="off" />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            {model ? 'Save changes' : 'Add model'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
