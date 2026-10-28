import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { LocalAppliance } from '../../db/db'
import { Modal } from '../my/Modal'
import { createAppliance, updateAppliance } from './applianceStore'
import type { ApplianceForm } from './applianceStore'

interface Props {
  /** The model the appliance belongs to. */
  applianceModelId: string
  /** The appliance to edit, or nothing to create a new one. */
  appliance?: LocalAppliance
  onClose: () => void
  onSaved: () => void
}

export function ApplianceFormModal({ applianceModelId, appliance, onClose, onSaved }: Props) {
  const [form, setForm] = useState<ApplianceForm>({
    serialNo: appliance?.serialNo ?? '',
    serialVerified: appliance?.serialVerified ?? false,
    identityNotes: appliance?.identityNotes ?? '',
  })
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    const clean: ApplianceForm = {
      serialNo: form.serialNo.trim(),
      serialVerified: form.serialVerified,
      identityNotes: form.identityNotes.trim(),
    }
    if (clean.serialVerified && clean.serialNo === '') {
      setError('Enter the serial number before marking it as verified.')
      return
    }
    try {
      if (appliance) {
        await updateAppliance(appliance, clean)
      } else {
        await createAppliance(applianceModelId, clean)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The appliance could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={appliance ? 'Edit appliance' : 'New appliance'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Serial number (leave empty if not known yet)
          <input
            value={form.serialNo}
            onChange={(event) => setForm({ ...form, serialNo: event.target.value })}
            autoComplete="off"
            autoFocus
          />
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={form.serialVerified}
            onChange={(event) => setForm({ ...form, serialVerified: event.target.checked })}
          />
          Serial number checked against the appliance
        </label>
        <label>
          Notes to recognise this appliance
          <textarea
            value={form.identityNotes}
            onChange={(event) => setForm({ ...form, identityNotes: event.target.value })}
            rows={3}
          />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            {appliance ? 'Save changes' : 'Add appliance'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
