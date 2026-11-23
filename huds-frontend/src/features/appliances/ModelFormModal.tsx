import { useState } from 'react'
import type { ChangeEventHandler, SubmitEventHandler } from 'react'
import type { ApplianceModelPayload } from '../../api/types'
import type { LocalModel } from '../../db/db'
import { Modal } from '../my/Modal'
import { APPLIANCE_TYPES } from './applianceTypes'
import { ACCEPTED_DOCUMENT_TYPES, createModelWithSchematic, listDocumentsOfType } from './documentStore'
import { createModel, updateModel } from './modelStore'

interface Props {
  /** The model to edit, or nothing to create a new one. */
  model?: LocalModel
  onClose: () => void
  /** Gets the id of the model when a new one was created. */
  onSaved: (applianceModelId?: string) => void
}

export function ModelFormModal({ model, onClose, onSaved }: Props) {
  const [form, setForm] = useState<ApplianceModelPayload>({
    applianceType: model?.applianceType ?? 'WASHER',
    brand: model?.brand ?? '',
    modelNo: model?.modelNo ?? '',
    // The documents are chosen on the model page; the form keeps them and sends them back unchanged.
    schematicDocumentId: model?.schematicDocumentId ?? null,
    schematicLocation: model?.schematicLocation ?? '',
    serviceManualDocumentId: model?.serviceManualDocumentId ?? null,
  })
  // A new model can get its schematic right away: the reference printed on it and, if at hand, the file.
  const [schematicRef, setSchematicRef] = useState('')
  const [schematicFile, setSchematicFile] = useState<File | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  const handleSchematicFile: ChangeEventHandler<HTMLInputElement> = (event) => {
    setSchematicFile(event.target.files?.[0])
  }

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    const clean: ApplianceModelPayload = {
      applianceType: form.applianceType,
      brand: form.brand.trim(),
      modelNo: form.modelNo.trim(),
      schematicDocumentId: form.schematicDocumentId,
      schematicLocation: form.schematicLocation.trim(),
      serviceManualDocumentId: form.serviceManualDocumentId,
    }
    let savedId: string | undefined
    try {
      if (model) {
        await updateModel(model, clean)
      } else {
        const ref = schematicRef.trim()
        if (schematicFile && ref === '') {
          setError('Enter the reference number printed on the schematic.')
          return
        }
        // Check the reference before the model is created, so a refusal does not leave a half-saved model behind.
        if (ref !== '') {
          const known = (await listDocumentsOfType('SCHEMATIC')).find((other) => other.documentRef.toLowerCase() === ref.toLowerCase())
          if (known) {
            setError(`A schematic with the reference "${known.documentRef}" exists already. Add the model first, then choose it on the model page.`)
            return
          }
        }
        // With a schematic the document is registered first and the model points at it from the start.
        savedId = ref === '' ? await createModel(clean) : await createModelWithSchematic(clean, ref, schematicFile)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The model could not be saved.')
      return
    }
    onSaved(savedId)
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
        <label>
          Where the wiring diagram is on the appliance (optional)
          <input
            value={form.schematicLocation}
            onChange={(event) => setForm({ ...form, schematicLocation: event.target.value })}
            autoComplete="off"
          />
        </label>
        {!model && (
          <>
            <label>
              Schematic reference number (optional)
              <input value={schematicRef} onChange={(event) => setSchematicRef(event.target.value)} maxLength={120} autoComplete="off" />
            </label>
            <label>
              Schematic file (optional, PDF, JPEG or PNG)
              <input type="file" accept={ACCEPTED_DOCUMENT_TYPES} onChange={handleSchematicFile} />
            </label>
          </>
        )}
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
