import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CurrentUser, PowerState, StepResult } from '../../api/types'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalDiagnosticStep, LocalVisit } from '../../db/db'
import { Modal } from '../my/Modal'
import { addStep, EMPTY_STEP_FORM, formOfStep, updateStep } from './diagnosticStepStore'
import type { StepForm } from './diagnosticStepStore'
import { labelOf, POWER_STATE_LABELS, STEP_RESULT_LABELS } from './visitRecordLabels'
import { visitRecordsBlockedReason } from './visitRecordGuard'

interface Props {
  visit: LocalVisit
  user: CurrentUser
  steps: LocalDiagnosticStep[]
  onSaved: () => void
}

/**
 * Section 3 of the form on the visit page: one row per hypothesis that was tested, with the measurement and the result.
 * Steps can be added and changed while the technician is on site; afterwards the list is only shown.
 */
export function DiagnosticStepsCard({ visit, user, steps, onSaved }: Props) {
  const blocked = visitRecordsBlockedReason(visit, user)
  const [dialog, setDialog] = useState<{ row?: LocalDiagnosticStep } | null>(null)

  return (
    <div className="card">
      <div className="page-head">
        <h3>Diagnostic steps</h3>
        {blocked === null && (
          <button type="button" onClick={() => setDialog({})}>
            + Add step
          </button>
        )}
      </div>
      {steps.length === 0 ? (
        <p className="muted">{blocked ?? 'No step recorded yet.'}</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Hypothesis</th>
                <th>Test</th>
                <th>Expected / actual</th>
                <th>Result</th>
                <th>Next step</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {steps.map((row) => (
                <tr key={row.diagnosticStepId}>
                  <td data-label="#">
                    {row.stepNo} <SyncBadge state={row.syncState} />
                  </td>
                  <td data-label="Hypothesis">{row.hypothesis}</td>
                  <td data-label="Test">
                    {row.testTarget}
                    {row.powerState && <span className="muted"> · {labelOf(POWER_STATE_LABELS, row.powerState)}</span>}
                  </td>
                  <td data-label="Expected / actual">{[row.expected, row.actual].filter(Boolean).join(' / ') || '-'}</td>
                  <td data-label="Result">{labelOf(STEP_RESULT_LABELS, row.result)}</td>
                  <td data-label="Next step">{row.nextStep}</td>
                  <td>
                    {blocked === null && (
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
        <StepModal
          visit={visit}
          user={user}
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
  visit: LocalVisit
  user: CurrentUser
  row?: LocalDiagnosticStep
  onClose: () => void
  onSaved: () => void
}

function StepModal({ visit, user, row, onClose, onSaved }: ModalProps) {
  const [form, setForm] = useState<StepForm>(row ? formOfStep(row) : EMPTY_STEP_FORM)
  const [error, setError] = useState<string | null>(null)
  const set = <K extends keyof StepForm>(key: K, value: StepForm[K]) => setForm((previous) => ({ ...previous, [key]: value }))

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      if (row) {
        await updateStep(visit, user, row, form)
      } else {
        await addStep(visit, user, form)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The step could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={row ? `Edit step ${row.stepNo}` : 'Add diagnostic step'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Hypothesis / possible cause
          <textarea value={form.hypothesis} onChange={(event) => set('hypothesis', event.target.value)} rows={2} autoFocus maxLength={1000} />
        </label>
        <label>
          Test / measurement (component, terminal pin or mode)
          <input value={form.testTarget} onChange={(event) => set('testTarget', event.target.value)} autoComplete="off" maxLength={500} />
        </label>
        <label>
          Power state during the test
          <select value={form.powerState} onChange={(event) => set('powerState', event.target.value as '' | PowerState)}>
            <option value="">Not recorded</option>
            {(Object.keys(POWER_STATE_LABELS) as PowerState[]).map((value) => (
              <option key={value} value={value}>
                {POWER_STATE_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Expected (with unit)
          <input value={form.expected} onChange={(event) => set('expected', event.target.value)} autoComplete="off" maxLength={200} />
        </label>
        <label>
          Actual (with unit)
          <input value={form.actual} onChange={(event) => set('actual', event.target.value)} autoComplete="off" maxLength={200} />
        </label>
        <label>
          Result
          <select value={form.result} onChange={(event) => set('result', event.target.value as '' | StepResult)}>
            <option value="">Not judged yet</option>
            {(Object.keys(STEP_RESULT_LABELS) as StepResult[]).map((value) => (
              <option key={value} value={value}>
                {STEP_RESULT_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Decision / next step
          <textarea value={form.nextStep} onChange={(event) => set('nextStep', event.target.value)} rows={2} maxLength={1000} />
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
