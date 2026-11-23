import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { ErrorCodeStatus } from '../../api/types'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalCase, LocalCaseErrorCode } from '../../db/db'
import { Modal } from '../my/Modal'
import { ERROR_CODE_STATUS_LABELS } from './caseLabels'
import { addErrorCode, updateErrorCode } from './caseErrorCodeStore'

interface Props {
  caseRecord: LocalCase
  codes: LocalCaseErrorCode[]
  editable: boolean
  onSaved: () => void
}

/** The error codes of the case, with adding and changing. A code is never deleted; it is moved to "History only". */
export function ErrorCodesCard({ caseRecord, codes, editable, onSaved }: Props) {
  const [dialog, setDialog] = useState<{ row?: LocalCaseErrorCode } | null>(null)
  return (
    <div className="card">
      <div className="page-head">
        <h3>Error codes</h3>
        {editable && (
          <button type="button" onClick={() => setDialog({})}>
            + Add error code
          </button>
        )}
      </div>
      {codes.length === 0 ? (
        <p className="muted">No error code listed.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Code</th>
                <th>Message on the appliance</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {codes.map((row) => (
                <tr key={row.caseErrorCodeId}>
                  <td data-label="Code">
                    <strong>{row.errorCode}</strong> <SyncBadge state={row.syncState} />
                  </td>
                  <td data-label="Message">{row.displayMessage}</td>
                  <td data-label="Status">{ERROR_CODE_STATUS_LABELS[row.codeStatus]}</td>
                  <td>
                    {editable && (
                      <button type="button" disabled={row.syncState === 'REJECTED'} onClick={() => setDialog({ row })}>
                        Edit
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog && (
        <ErrorCodeModal
          caseRecord={caseRecord}
          row={dialog.row}
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
  row?: LocalCaseErrorCode
  onClose: () => void
  onSaved: () => void
}

function ErrorCodeModal({ caseRecord, row, onClose, onSaved }: ModalProps) {
  const [errorCode, setErrorCode] = useState(row?.errorCode ?? '')
  const [displayMessage, setDisplayMessage] = useState(row?.displayMessage ?? '')
  const [codeStatus, setCodeStatus] = useState<ErrorCodeStatus>(row?.codeStatus ?? 'ACTIVE')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      const form = { errorCode, displayMessage, codeStatus }
      if (row) {
        await updateErrorCode(caseRecord, row, form)
      } else {
        await addErrorCode(caseRecord, form)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The error code could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={row ? 'Edit error code' : 'Add error code'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Code
          <input value={errorCode} onChange={(event) => setErrorCode(event.target.value)} autoComplete="off" autoFocus maxLength={100} />
        </label>
        <label>
          Message shown on the appliance (optional)
          <input value={displayMessage} onChange={(event) => setDisplayMessage(event.target.value)} autoComplete="off" maxLength={500} />
        </label>
        <label>
          Status
          <select value={codeStatus} onChange={(event) => setCodeStatus(event.target.value as ErrorCodeStatus)}>
            {(Object.keys(ERROR_CODE_STATUS_LABELS) as ErrorCodeStatus[]).map((status) => (
              <option key={status} value={status}>
                {ERROR_CODE_STATUS_LABELS[status]}
              </option>
            ))}
          </select>
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
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
