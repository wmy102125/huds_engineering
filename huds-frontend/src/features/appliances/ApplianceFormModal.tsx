import { useCallback, useEffect, useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { LocalAppliance, LocalCustomer, LocalModel } from '../../db/db'
import { listCustomers } from '../customers/customerStore'
import { Modal } from '../my/Modal'
import { createAppliance, updateAppliance } from './applianceStore'
import type { ApplianceForm } from './applianceStore'
import { applianceTypeLabel } from './applianceTypes'
import { ModelFormModal } from './ModelFormModal'
import { listModels } from './modelStore'

interface Props {
  /** Set when the customer is already known (opened from a customer page); otherwise the user picks one. */
  customerId?: string
  /** Set when the model is already known (opened from a model page); otherwise the user picks one. */
  applianceModelId?: string
  /** The appliance to edit, or nothing to create a new one. The owner and the model of an existing appliance cannot change. */
  appliance?: LocalAppliance
  onClose: () => void
  /** Gets the id of the appliance when a new one was created. */
  onSaved: (applianceId?: string) => void
}

function modelText(model: LocalModel): string {
  return [applianceTypeLabel(model.applianceType), model.brand, model.modelNo].filter(Boolean).join(' ')
}

export function ApplianceFormModal({ customerId, applianceModelId, appliance, onClose, onSaved }: Props) {
  const [form, setForm] = useState<ApplianceForm>({
    serialNo: appliance?.serialNo ?? '',
    serialVerified: appliance?.serialVerified ?? false,
    identityNotes: appliance?.identityNotes ?? '',
    customerAssetRef: appliance?.customerAssetRef ?? '',
    purchaseOrderNumber: appliance?.purchaseOrderNumber ?? '',
    productionDate: appliance?.productionDate ?? null,
    estimatedAgeYears: appliance?.estimatedAgeYears ?? null,
  })
  const [customers, setCustomers] = useState<LocalCustomer[]>([])
  const [models, setModels] = useState<LocalModel[]>([])
  const [customerChoice, setCustomerChoice] = useState(customerId ?? appliance?.customerId ?? '')
  const [modelChoice, setModelChoice] = useState(applianceModelId ?? appliance?.applianceModelId ?? '')
  const [newModelOpen, setNewModelOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const customerFixed = customerId !== undefined || appliance !== undefined
  const modelFixed = applianceModelId !== undefined || appliance !== undefined

  const loadChoices = useCallback(async () => {
    setCustomers(await listCustomers())
    setModels(await listModels())
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void loadChoices(), 0)
    return () => window.clearTimeout(timer)
  }, [loadChoices])

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    const clean: ApplianceForm = {
      serialNo: form.serialNo.trim(),
      serialVerified: form.serialVerified,
      identityNotes: form.identityNotes.trim(),
      customerAssetRef: form.customerAssetRef.trim(),
      purchaseOrderNumber: form.purchaseOrderNumber.trim(),
      productionDate: form.productionDate || null,
      estimatedAgeYears: form.estimatedAgeYears,
    }
    if (clean.estimatedAgeYears !== null && (!Number.isInteger(clean.estimatedAgeYears) || clean.estimatedAgeYears < 0)) {
      setError('The age must be a whole number of years, 0 or more.')
      return
    }
    if (clean.serialVerified && clean.serialNo === '') {
      setError('Enter the serial number before marking it as verified.')
      return
    }
    if (!appliance && (customerChoice === '' || modelChoice === '')) {
      setError('Please choose the customer and the model.')
      return
    }
    let savedId: string | undefined
    try {
      if (appliance) {
        await updateAppliance(appliance, clean)
      } else {
        savedId = await createAppliance(customerChoice, modelChoice, clean)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The appliance could not be saved.')
      return
    }
    onSaved(savedId)
  }

  const fixedCustomer = customers.find((customer) => customer.customerId === customerChoice)
  const fixedModel = models.find((model) => model.applianceModelId === modelChoice)

  return (
    <>
      <Modal title={appliance ? 'Edit appliance' : 'New appliance'} onClose={onClose}>
        <form className="stack" onSubmit={handleSubmit}>
          <label>
            Customer
            {customerFixed ? (
              <input value={fixedCustomer?.customerName ?? ''} readOnly disabled />
            ) : (
              <select value={customerChoice} onChange={(event) => setCustomerChoice(event.target.value)} autoFocus>
                <option value="">Choose a customer...</option>
                {customers.map((customer) => (
                  <option key={customer.customerId} value={customer.customerId}>
                    {customer.customerName}
                    {customer.phone ? ` · ${customer.phone}` : ''}
                  </option>
                ))}
              </select>
            )}
          </label>
          <label>
            Model
            {modelFixed ? (
              <input value={fixedModel ? modelText(fixedModel) : ''} readOnly disabled />
            ) : (
              <span className="row">
                <select value={modelChoice} onChange={(event) => setModelChoice(event.target.value)}>
                  <option value="">Choose a model...</option>
                  {models.map((model) => (
                    <option key={model.applianceModelId} value={model.applianceModelId}>
                      {modelText(model)}
                    </option>
                  ))}
                </select>
                <button type="button" onClick={() => setNewModelOpen(true)}>
                  + New model
                </button>
              </span>
            )}
          </label>
          <label>
            Serial number (leave empty if not known yet)
            <input
              value={form.serialNo}
              onChange={(event) => setForm({ ...form, serialNo: event.target.value })}
              autoComplete="off"
              autoFocus={customerFixed}
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
          <label>
            Customer's own asset number (optional)
            <input
              value={form.customerAssetRef}
              onChange={(event) => setForm({ ...form, customerAssetRef: event.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            Purchase order number (optional)
            <input
              value={form.purchaseOrderNumber}
              onChange={(event) => setForm({ ...form, purchaseOrderNumber: event.target.value })}
              autoComplete="off"
            />
          </label>
          <label>
            Production date (optional)
            <input
              type="date"
              value={form.productionDate ?? ''}
              onChange={(event) => setForm({ ...form, productionDate: event.target.value || null })}
            />
          </label>
          <label>
            Estimated age in years (optional, when the production date is not known)
            <input
              type="number"
              min={0}
              value={form.estimatedAgeYears ?? ''}
              onChange={(event) => setForm({ ...form, estimatedAgeYears: event.target.value === '' ? null : Number(event.target.value) })}
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
      {newModelOpen && (
        <ModelFormModal
          onClose={() => setNewModelOpen(false)}
          onSaved={(savedId) => {
            setNewModelOpen(false)
            if (savedId) {
              setModelChoice(savedId)
            }
            void loadChoices()
          }}
        />
      )}
    </>
  )
}
