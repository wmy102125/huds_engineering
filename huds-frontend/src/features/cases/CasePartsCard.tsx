import { useEffect, useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { PartsSupplyStatus } from '../../api/types'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalCase, LocalCasePart } from '../../db/db'
import { Modal } from '../my/Modal'
import {
  addPart,
  clearAddPartRequest,
  EMPTY_PART_FORM,
  formOfPart,
  hasAddPartRequest,
  isPartWaiting,
  markPartArrived,
  PARTS_STATUS_LABELS,
  removePart,
  updatePart,
} from './casePartStore'
import type { CasePartForm } from './casePartStore'

interface Props {
  caseRecord: LocalCase
  parts: LocalCasePart[]
  editable: boolean
  onSaved: () => void
}

/** The date line under the supply status badge: when the part arrived or is expected. */
function supplyDetail(part: LocalCasePart): string | null {
  if (part.partsSupplyStatus === 'ARRIVED' && part.arrivedOn) {
    return `on ${part.arrivedOn}`
  }
  if (isPartWaiting(part) && part.expectedArrivalOn) {
    return `expected ${part.expectedArrivalOn}`
  }
  return null
}

/** The parts of the case: what is being waited for, what is in hand, and the warranty of each. */
export function CasePartsCard({ caseRecord, parts, editable, onSaved }: Props) {
  // A visit that ended with "Parts required" sends the person here with the dialog already open.
  const [dialog, setDialog] = useState<{ part?: LocalCasePart } | null>(() =>
    editable && hasAddPartRequest(caseRecord.diagnosticCaseId) ? {} : null,
  )
  useEffect(() => {
    clearAddPartRequest()
  }, [])
  const [error, setError] = useState<string | null>(null)

  const arrived = async (part: LocalCasePart) => {
    setError(null)
    try {
      await markPartArrived(caseRecord, part)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The part could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <div className="card">
      <div className="page-head">
        <h3>Parts</h3>
        {editable && (
          <button type="button" onClick={() => setDialog({})}>
            + Add part
          </button>
        )}
      </div>
      {parts.length === 0 ? (
        <p className="muted">
          No part listed.{caseRecord.workflowStatus === 'PENDING_PARTS' && ' Add the parts this case is waiting for.'}
        </p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Part</th>
                <th>Qty</th>
                <th>Supply</th>
                <th>Warranty</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {parts.map((part) => (
                <tr key={part.casePartId}>
                  <td data-label="Part">
                    <strong>{part.partName}</strong> <SyncBadge state={part.syncState} />
                    {(part.oemPartNo || part.supplier) && (
                      <div className="muted small">{[part.oemPartNo, part.supplier].filter(Boolean).join(' · ')}</div>
                    )}
                  </td>
                  <td data-label="Qty">{[part.quantity, part.unit].filter((item) => item !== '').join(' ')}</td>
                  <td data-label="Supply">
                    <span className={`badge ${isPartWaiting(part) ? 'wait' : 'ok'}`}>{PARTS_STATUS_LABELS[part.partsSupplyStatus]}</span>
                    {supplyDetail(part) && <div className="muted small">{supplyDetail(part)}</div>}
                  </td>
                  <td data-label="Warranty">{part.warrantyMonths === null ? '-' : `${part.warrantyMonths} months`}</td>
                  <td>
                    {editable && (
                      <div className="row">
                        {isPartWaiting(part) && (
                          <button type="button" className="primary" disabled={part.syncState === 'REJECTED'} onClick={() => void arrived(part)}>
                            Mark arrived
                          </button>
                        )}
                        <button type="button" disabled={part.syncState === 'REJECTED'} onClick={() => setDialog({ part })}>
                          Edit
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {error && <p className="error">{error}</p>}
      {dialog && (
        <PartModal
          caseRecord={caseRecord}
          part={dialog.part}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null)
            onSaved()
          }}
        />
      )}
    </div>
  )
}

interface ModalProps {
  caseRecord: LocalCase
  part?: LocalCasePart
  onClose: () => void
  onSaved: () => void
}

function PartModal({ caseRecord, part, onClose, onSaved }: ModalProps) {
  const [form, setForm] = useState<CasePartForm>(part ? formOfPart(part) : EMPTY_PART_FORM)
  const [error, setError] = useState<string | null>(null)
  const [confirmRemove, setConfirmRemove] = useState(false)

  const set = <K extends keyof CasePartForm>(key: K, value: CasePartForm[K]) => setForm((current) => ({ ...current, [key]: value }))

  const run = async (action: () => Promise<void>) => {
    setError(null)
    try {
      await action()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The part could not be saved.')
      return
    }
    onSaved()
  }

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault()
    void run(() => (part ? updatePart(caseRecord, part, form) : addPart(caseRecord, form)))
  }

  const status = form.partsSupplyStatus
  return (
    <Modal title={part ? 'Edit part' : 'Add part'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Part name
          <input value={form.partName} onChange={(event) => set('partName', event.target.value)} autoComplete="off" autoFocus maxLength={200} />
        </label>
        <label>
          Part number (optional)
          <input value={form.oemPartNo} onChange={(event) => set('oemPartNo', event.target.value)} autoComplete="off" maxLength={100} />
        </label>
        <div className="row">
          <label>
            Quantity
            <input value={form.quantity} onChange={(event) => set('quantity', event.target.value)} inputMode="decimal" />
          </label>
          <label>
            Unit (optional)
            <input value={form.unit} onChange={(event) => set('unit', event.target.value)} autoComplete="off" maxLength={50} placeholder="pcs, m ..." />
          </label>
        </div>
        <label>
          Supplier (optional)
          <input value={form.supplier} onChange={(event) => set('supplier', event.target.value)} autoComplete="off" maxLength={200} />
        </label>
        <label>
          Supply
          <select value={status} onChange={(event) => set('partsSupplyStatus', event.target.value as PartsSupplyStatus)}>
            {(Object.keys(PARTS_STATUS_LABELS) as PartsSupplyStatus[]).map((value) => (
              <option key={value} value={value}>
                {PARTS_STATUS_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        {(status === 'ORDERED' || status === 'BACKORDER' || status === 'ARRIVED') && (
          <label>
            Ordered on (optional)
            <input type="date" value={form.orderedOn} onChange={(event) => set('orderedOn', event.target.value)} />
          </label>
        )}
        {(status === 'ORDERED' || status === 'BACKORDER') && (
          <label>
            Expected to arrive (optional)
            <input type="date" value={form.expectedArrivalOn} onChange={(event) => set('expectedArrivalOn', event.target.value)} />
          </label>
        )}
        {status === 'ARRIVED' && (
          <label>
            Arrived on
            <input type="date" value={form.arrivedOn} onChange={(event) => set('arrivedOn', event.target.value)} />
          </label>
        )}
        <label>
          Warranty after installation, in months (optional)
          <input value={form.warrantyMonths} onChange={(event) => set('warrantyMonths', event.target.value)} inputMode="numeric" placeholder="12" />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          {part && !confirmRemove && (
            <button type="button" onClick={() => setConfirmRemove(true)}>
              Remove part
            </button>
          )}
          {part && confirmRemove && (
            <button type="button" onClick={() => void run(() => removePart(caseRecord, part))}>
              Really remove
            </button>
          )}
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            Save
          </button>
        </div>
      </form>
    </Modal>
  )
}
