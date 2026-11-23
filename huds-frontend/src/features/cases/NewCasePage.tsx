import { useCallback, useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { LocalAddress, LocalAppliance, LocalCase, LocalCustomer } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import { navigate } from '../../routes/useRoute'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { AddressFormModal } from '../addresses/AddressFormModal'
import { listAddresses } from '../addresses/addressStore'
import { ApplianceFormModal } from '../appliances/ApplianceFormModal'
import { addAttachment } from '../attachments/attachmentStore'
import { FilesDropTextarea, PendingFilesList } from '../attachments/PendingFiles'
import { usePendingFiles } from '../attachments/pendingFilesState'
import { listAppliancesOfCustomer } from '../appliances/applianceStore'
import { applianceTypeLabel } from '../appliances/applianceTypes'
import { getModel } from '../appliances/modelStore'
import { CustomerFormModal } from '../customers/CustomerFormModal'
import { listCustomers } from '../customers/customerStore'
import { CaseClassificationFields, EMPTY_CLASSIFICATION } from './CaseClassificationFields'
import type { ClassificationForm } from './CaseClassificationFields'
import { statusLabel } from './caseLabels'
import { createCase, listUnfinishedCasesOfAppliance } from './caseStore'

interface Props {
  sync: SyncStatus
  /** Set when the page is opened from an appliance: that customer and appliance are chosen already. */
  initialCustomerId?: string
  initialApplianceId?: string
}

interface Loaded {
  customers: LocalCustomer[]
  addresses: LocalAddress[]
  appliances: { appliance: LocalAppliance; modelText: string }[]
  unfinished: LocalCase[]
}

type Dialog = 'customer' | 'address' | 'appliance' | null

/**
 * Opens a case ("开单"): who, where, which appliance, and what is wrong.
 * Everything can be created on the spot, also while offline; the case then waits in the outbox
 * behind the customer, address and appliance it needs.
 */
export function NewCasePage({ sync, initialCustomerId, initialApplianceId }: Props) {
  // Opened from an appliance page: the customer and the appliance are already chosen.
  const [customerId, setCustomerId] = useState(initialCustomerId ?? '')
  const [addressChoice, setAddressChoice] = useState('')
  const [applianceChoice, setApplianceChoice] = useState(initialApplianceId ?? '')
  const [symptoms, setSymptoms] = useState('')
  const [classification, setClassification] = useState<ClassificationForm>(EMPTY_CLASSIFICATION)
  const [errorCodes, setErrorCodes] = useState<{ errorCode: string; displayMessage: string }[]>([])
  // Photos and videos chosen while describing the problem. They are saved with the case when it is opened.
  const pending = usePendingFiles()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (): Promise<Loaded> => {
    // Only the appliances of the chosen customer can be picked.
    const appliances = []
    for (const appliance of customerId ? await listAppliancesOfCustomer(customerId) : []) {
      const model = await getModel(appliance.applianceModelId)
      const modelText = model ? [applianceTypeLabel(model.applianceType), model.brand, model.modelNo].filter(Boolean).join(' ') : ''
      appliances.push({ appliance, modelText })
    }
    return {
      customers: await listCustomers(),
      addresses: customerId ? await listAddresses(customerId) : [],
      appliances,
      unfinished: applianceChoice ? await listUnfinishedCasesOfAppliance(applianceChoice) : [],
    }
  }, [customerId, applianceChoice])
  const { data, reload } = useLocalQuery(load, sync.tick)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const { customers, addresses, appliances, unfinished } = data

  // A choice that no longer fits (another customer was picked) falls back to the customer's default address.
  const addressId = addresses.some((address) => address.serviceAddressId === addressChoice)
    ? addressChoice
    : (addresses.find((address) => address.isDefault) ?? addresses[0])?.serviceAddressId ?? ''
  const applianceId = appliances.some((row) => row.appliance.applianceId === applianceChoice) ? applianceChoice : ''

  const closeDialog = () => setDialog(null)
  const afterDialog = () => {
    setDialog(null)
    void reload()
  }

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    if (customerId === '' || addressId === '' || applianceId === '') {
      setError('Please choose the customer, the address and the appliance.')
      return
    }
    if (unfinished.length > 0) {
      setError('This appliance already has an unfinished case. Finish or cancel that case first.')
      return
    }
    let caseId: string
    try {
      caseId = await createCase({
        customerId,
        serviceAddressId: addressId,
        applianceId,
        reportedSymptoms: symptoms,
        classification,
        // Empty rows are ignored.
        errorCodes: errorCodes
          .filter((row) => row.errorCode.trim() !== '')
          .map((row) => ({ ...row, codeStatus: 'ACTIVE' as const })),
      })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The case could not be saved.')
      return
    }
    // The case is saved. The files wait behind it in the outbox; a file that fails does not undo the case.
    const failed: string[] = []
    for (const file of pending.files) {
      try {
        await addAttachment('CASE', caseId, file)
      } catch (caught) {
        failed.push(caught instanceof Error ? caught.message : `"${file.name}" could not be added.`)
      }
    }
    if (failed.length > 0) {
      window.alert(`The case was opened, but some files were not added. You can add them again on the case page.\n${failed.join('\n')}`)
    }
    void sync.requestSync()
    navigate(`/my/cases/${caseId}`)
  }

  return (
    <section>
      <a className="back" href="#/my/cases">
        ← Cases
      </a>
      <div className="page-head">
        <h2>New case</h2>
      </div>

      <form className="card stack" onSubmit={handleSubmit}>
        <h3>Customer</h3>
        <div className="row">
          <select value={customerId} onChange={(event) => setCustomerId(event.target.value)} aria-label="Customer">
            <option value="">Choose a customer...</option>
            {customers.map((customer) => (
              <option key={customer.customerId} value={customer.customerId}>
                {customer.customerName}
                {customer.phone ? ` · ${customer.phone}` : ''}
              </option>
            ))}
          </select>
          <button type="button" onClick={() => setDialog('customer')}>
            + New customer
          </button>
        </div>

        <h3>Service address</h3>
        {customerId === '' ? (
          <p className="muted">Choose the customer first.</p>
        ) : (
          <div className="row">
            <select value={addressId} onChange={(event) => setAddressChoice(event.target.value)} aria-label="Service address">
              {addresses.length === 0 && <option value="">This customer has no address yet</option>}
              {addresses.map((address) => (
                <option key={address.serviceAddressId} value={address.serviceAddressId}>
                  {address.addressLine1}, {address.city}
                  {address.isDefault ? ' (default)' : ''}
                </option>
              ))}
            </select>
            <button type="button" onClick={() => setDialog('address')}>
              + New address
            </button>
          </div>
        )}

        <h3>Appliance</h3>
        {customerId === '' ? (
          <p className="muted">Choose the customer first.</p>
        ) : (
          <div className="row">
            <select
              value={applianceId}
              onChange={(event) => setApplianceChoice(event.target.value)}
              aria-label="Appliance"
            >
              <option value="">{appliances.length === 0 ? 'This customer has no appliance yet' : 'Choose an appliance...'}</option>
              {appliances.map(({ appliance, modelText }) => (
                <option key={appliance.applianceId} value={appliance.applianceId}>
                  {modelText}
                  {appliance.serialNo ? ` · Serial ${appliance.serialNo}` : ' · No serial number'}
                  {appliance.identityNotes ? ` · ${appliance.identityNotes}` : ''}
                </option>
              ))}
            </select>
            <button type="button" onClick={() => setDialog('appliance')}>
              + New appliance
            </button>
          </div>
        )}
        {unfinished.length > 0 && (
          <p className="error">
            This appliance already has a case that is not finished:{' '}
            {unfinished.map((item, index) => (
              <span key={item.diagnosticCaseId}>
                {index > 0 && ', '}
                <a href={`#/my/cases/${item.diagnosticCaseId}`}>{item.caseNo || 'number pending'}</a> ({statusLabel(item.workflowStatus)})
              </span>
            ))}
            . A new case can be opened after it is closed or cancelled.
          </p>
        )}

        <h3>Problem</h3>
        <label>
          What the customer reports
          <FilesDropTextarea value={symptoms} onChange={(event) => setSymptoms(event.target.value)} rows={4} onFiles={pending.add} />
        </label>
        <PendingFilesList files={pending.files} onPick={pending.add} onRemove={pending.remove} problem={pending.problem} />
        <CaseClassificationFields value={classification} onChange={setClassification} />
        <fieldset>
          <legend>Error codes shown on the appliance (optional)</legend>
          {errorCodes.map((row, index) => (
            <div className="row" key={index}>
              <input
                value={row.errorCode}
                placeholder="Code"
                aria-label="Error code"
                maxLength={100}
                autoComplete="off"
                onChange={(event) => setErrorCodes(errorCodes.map((item, at) => (at === index ? { ...item, errorCode: event.target.value } : item)))}
              />
              <input
                value={row.displayMessage}
                placeholder="Message (optional)"
                aria-label="Message"
                maxLength={500}
                autoComplete="off"
                onChange={(event) => setErrorCodes(errorCodes.map((item, at) => (at === index ? { ...item, displayMessage: event.target.value } : item)))}
              />
              <button type="button" onClick={() => setErrorCodes(errorCodes.filter((_, at) => at !== index))}>
                Remove
              </button>
            </div>
          ))}
          <button type="button" onClick={() => setErrorCodes([...errorCodes, { errorCode: '', displayMessage: '' }])}>
            + Add error code
          </button>
        </fieldset>
        {error && <p className="error">{error}</p>}
        <p className="muted small">The case number is given by the server. A case opened offline shows "Number pending" until it has been sent.</p>
        <div className="row end">
          <a className="button" href="#/my/cases">
            Cancel
          </a>
          <button type="submit" className="primary" disabled={unfinished.length > 0}>
            Open case
          </button>
        </div>
      </form>

      {dialog === 'customer' && (
        <CustomerFormModal
          onClose={closeDialog}
          onSaved={(savedId) => {
            if (savedId) {
              setCustomerId(savedId)
              setAddressChoice('')
            }
            afterDialog()
            void sync.requestSync()
          }}
        />
      )}
      {dialog === 'address' && customerId !== '' && (
        <AddressFormModal
          customerId={customerId}
          onClose={closeDialog}
          onSaved={(savedId) => {
            if (savedId) {
              setAddressChoice(savedId)
            }
            afterDialog()
            void sync.requestSync()
          }}
        />
      )}
      {dialog === 'appliance' && customerId !== '' && (
        <ApplianceFormModal
          customerId={customerId}
          onClose={closeDialog}
          onSaved={(savedId) => {
            if (savedId) {
              setApplianceChoice(savedId)
            }
            afterDialog()
            void sync.requestSync()
          }}
        />
      )}
    </section>
  )
}
