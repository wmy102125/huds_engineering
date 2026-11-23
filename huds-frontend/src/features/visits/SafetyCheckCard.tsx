import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CurrentUser, SafetyCheckCode } from '../../api/types'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalVisit, LocalVisitSafetyCheck } from '../../db/db'
import { GAS_LABELS, SAFETY_CHECK_CODES, SAFETY_CHECK_LABELS, SUPPLY_TYPE_LABELS } from './safetyCheckLabels'
import { formFrom, safetyCheckBlockedReason, saveSafetyCheck } from './safetyCheckStore'
import type { SafetyCheckForm } from './safetyCheckStore'

interface Props {
  visit: LocalVisit
  user: CurrentUser
  record: LocalVisitSafetyCheck | undefined
  onSaved: () => void
}

function unit(value: number | undefined, suffix: string): string {
  return value === undefined ? '-' : `${value} ${suffix}`
}

/**
 * Section 2 of the form for one visit, shown on the visit page: what was checked before the repair,
 * and the readings of supply, water and gas. While the visit is on site the technician fills it in here and saves it;
 * the visit cannot be completed before that. Afterwards (or for another technician's visit) it is only shown.
 */
export function SafetyCheckCard({ visit, user, record, onSaved }: Props) {
  const blocked = safetyCheckBlockedReason(visit, user)
  const refused = record?.syncState === 'REJECTED'
  const editable = blocked === null && !refused

  // What the user has typed and not saved yet. Without it the form shows the saved record.
  const [draft, setDraft] = useState<SafetyCheckForm | null>(null)
  const [error, setError] = useState<string | null>(null)
  const form = draft ?? formFrom(record)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await saveSafetyCheck(visit, user, form)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The checks could not be saved.')
      return
    }
    setDraft(null)
    onSaved()
  }

  const change = (next: SafetyCheckForm) => setDraft(next)
  const toggle = (code: SafetyCheckCode, checked: boolean) =>
    change({ ...form, safetyChecks: { ...form.safetyChecks, [code]: checked } })
  const number = (key: 'l1nV' | 'l2nV' | 'l1l2V' | 'waterTempF' | 'pressureStaticPsi' | 'pressureDynamicPsi', label: string) => (
    <label>
      {label}
      <input type="number" step="any" min={0} value={form[key]} onChange={(event) => change({ ...form, [key]: event.target.value })} />
    </label>
  )

  const heading = (
    <h3>
      Safety checks {record && <SyncBadge state={record.syncState} />}
    </h3>
  )

  if (!editable) {
    // Read only: a finished visit, a visit of another technician, or a change the server refused.
    const ticked = record ? SAFETY_CHECK_CODES.filter((code) => record.safetyChecks[code]) : []
    return (
      <div className="card">
        {heading}
        {!record && <p className="muted">{blocked ?? 'Nothing recorded.'}</p>}
        {record && (
          <dl className="facts">
            <dt>Checks done</dt>
            <dd>{ticked.length === 0 ? '-' : ticked.map((code) => SAFETY_CHECK_LABELS[code]).join(', ')}</dd>
            <dt>Supply</dt>
            <dd>{record.utilityBaseline.supplyType ? SUPPLY_TYPE_LABELS[record.utilityBaseline.supplyType] : '-'}</dd>
            <dt>Voltage</dt>
            <dd>
              L1-N {unit(record.utilityBaseline.l1nV, 'V')} · L2-N {unit(record.utilityBaseline.l2nV, 'V')} · L1-L2{' '}
              {unit(record.utilityBaseline.l1l2V, 'V')}
            </dd>
            <dt>Water</dt>
            <dd>
              {unit(record.environmentBaseline.waterTempF, '°F')} · static {unit(record.environmentBaseline.pressureStaticPsi, 'psi')} ·
              dynamic {unit(record.environmentBaseline.pressureDynamicPsi, 'psi')}
            </dd>
            <dt>Gas</dt>
            <dd>{record.environmentBaseline.gas ? GAS_LABELS[record.environmentBaseline.gas] : '-'}</dd>
            <dt>Notes</dt>
            <dd>{record.environmentBaseline.ambientNotes || '-'}</dd>
          </dl>
        )}
        {record && <AuditLine record={record} />}
        {refused && <p className="notice">The server refused a change to the safety checks. See "Needs attention" at the top of the page.</p>}
      </div>
    )
  }

  return (
    <form className="card stack" onSubmit={handleSubmit}>
      {heading}
      {!record && <p className="muted small">Fill in and save the checks. The visit can be completed after that.</p>}
      <fieldset>
        <legend>Checked before the repair</legend>
        {SAFETY_CHECK_CODES.map((code) => (
          <label key={code} className="check">
            <input type="checkbox" checked={form.safetyChecks[code] === true} onChange={(event) => toggle(code, event.target.checked)} />
            {SAFETY_CHECK_LABELS[code]}
          </label>
        ))}
      </fieldset>
      <label>
        Supply type
        <select value={form.supplyType} onChange={(event) => change({ ...form, supplyType: event.target.value as SafetyCheckForm['supplyType'] })}>
          <option value="">Not recorded</option>
          {Object.entries(SUPPLY_TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      {number('l1nV', 'L1-N voltage (V)')}
      {number('l2nV', 'L2-N voltage (V)')}
      {number('l1l2V', 'L1-L2 voltage (V)')}
      {number('waterTempF', 'Water temperature (°F)')}
      {number('pressureStaticPsi', 'Static water pressure (psi)')}
      {number('pressureDynamicPsi', 'Dynamic water pressure (psi)')}
      <label>
        Gas
        <select value={form.gas} onChange={(event) => change({ ...form, gas: event.target.value as SafetyCheckForm['gas'] })}>
          <option value="">Not recorded</option>
          {Object.entries(GAS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Notes about the surroundings
        <textarea value={form.ambientNotes} onChange={(event) => change({ ...form, ambientNotes: event.target.value })} rows={3} />
      </label>
      {error && <p className="error">{error}</p>}
      {record && <AuditLine record={record} />}
      <div className="row end">
        <button type="submit" className="primary" disabled={draft === null && record !== undefined}>
          Save checks
        </button>
      </div>
    </form>
  )
}
