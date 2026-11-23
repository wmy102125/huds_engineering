import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CurrentUser, CustomerDecision, DiagnosticOutcome, FailureCategory, RepairStatus } from '../../api/types'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalCase, LocalCaseSummary } from '../../db/db'
import { labelOf } from '../visits/visitRecordLabels'
import {
  CUSTOMER_DECISION_LABELS,
  DIAGNOSTIC_OUTCOME_LABELS,
  FAILURE_CATEGORIES,
  FAILURE_CATEGORY_LABELS,
  REPAIR_STATUS_LABELS,
} from './caseSummaryLabels'
import { saveCaseSummary, summaryBlockedReason, summaryFormFrom } from './caseSummaryStore'
import type { SummaryForm } from './caseSummaryStore'

interface Props {
  caseRecord: LocalCase
  user: CurrentUser
  record: LocalCaseSummary | undefined
  onSaved: () => void
}

/** A select over the values of a label table, with an empty "Not recorded" choice. */
function Choice<T extends string>(props: {
  label: string
  labels: Record<T, string>
  value: '' | T
  onChange: (value: '' | T) => void
}) {
  return (
    <label>
      {props.label}
      <select value={props.value} onChange={(event) => props.onChange(event.target.value as '' | T)}>
        <option value="">Not recorded</option>
        {(Object.keys(props.labels) as T[]).map((value) => (
          <option key={value} value={value}>
            {props.labels[value]}
          </option>
        ))}
      </select>
    </label>
  )
}

/** The summary of the case (sections 6 and 7): filled in and saved on the case page until the case is closed. */
export function CaseSummaryCard({ caseRecord, user, record, onSaved }: Props) {
  const blocked = summaryBlockedReason(caseRecord, user)
  const refused = record?.syncState === 'REJECTED'
  const editable = blocked === null && !refused

  const [draft, setDraft] = useState<SummaryForm | null>(null)
  const [error, setError] = useState<string | null>(null)
  const form = draft ?? summaryFormFrom(record)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await saveCaseSummary(caseRecord, user, record, form)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The summary could not be saved.')
      return
    }
    setDraft(null)
    onSaved()
  }

  const heading = (
    <h3>
      Case summary {record && <SyncBadge state={record.syncState} />}
    </h3>
  )

  if (!editable) {
    return (
      <div className="card">
        {heading}
        {!record && <p className="muted">{blocked ?? 'Nothing recorded.'}</p>}
        {record && (
          <dl className="facts">
            <dt>Diagnosis</dt>
            <dd>{labelOf(DIAGNOSTIC_OUTCOME_LABELS, record.diagnosticOutcome)}</dd>
            <dt>Repair</dt>
            <dd>{labelOf(REPAIR_STATUS_LABELS, record.repairStatus)}</dd>
            <dt>Customer decision</dt>
            <dd>{labelOf(CUSTOMER_DECISION_LABELS, record.customerDecision)}</dd>
            <dt>Failure categories</dt>
            <dd>{record.failureCategories.map((code) => FAILURE_CATEGORY_LABELS[code]).join(', ') || '-'}</dd>
            <dt>Main category</dt>
            <dd>{labelOf(FAILURE_CATEGORY_LABELS, record.primaryFailureCategory)}</dd>
            <dt>Tags</dt>
            <dd>{record.dataTags || '-'}</dd>
            <dt>Summary</dt>
            <dd>{record.technicianSummary || '-'}</dd>
          </dl>
        )}
        {record && <AuditLine record={record} />}
        {refused && <p className="notice">The server refused a change to the summary. See "Needs attention" at the top of the page.</p>}
      </div>
    )
  }

  const toggle = (code: FailureCategory, checked: boolean) => {
    const failureCategories = checked ? [...form.failureCategories, code] : form.failureCategories.filter((item) => item !== code)
    // The main category has to stay one of the ticked ones.
    const primaryFailureCategory = failureCategories.includes(form.primaryFailureCategory as FailureCategory) ? form.primaryFailureCategory : ''
    setDraft({ ...form, failureCategories, primaryFailureCategory })
  }

  return (
    <form className="card stack" onSubmit={handleSubmit}>
      {heading}
      <Choice<DiagnosticOutcome>
        label="What the diagnosis found"
        labels={DIAGNOSTIC_OUTCOME_LABELS}
        value={form.diagnosticOutcome}
        onChange={(value) => setDraft({ ...form, diagnosticOutcome: value })}
      />
      <Choice<RepairStatus>
        label="How far the repair went"
        labels={REPAIR_STATUS_LABELS}
        value={form.repairStatus}
        onChange={(value) => setDraft({ ...form, repairStatus: value })}
      />
      <Choice<CustomerDecision>
        label="What the customer decided"
        labels={CUSTOMER_DECISION_LABELS}
        value={form.customerDecision}
        onChange={(value) => setDraft({ ...form, customerDecision: value })}
      />
      <fieldset>
        <legend>Subsystems involved</legend>
        {FAILURE_CATEGORIES.map((code) => (
          <label key={code} className="check">
            <input type="checkbox" checked={form.failureCategories.includes(code)} onChange={(event) => toggle(code, event.target.checked)} />
            {FAILURE_CATEGORY_LABELS[code]}
          </label>
        ))}
      </fieldset>
      <label>
        Main subsystem
        <select
          value={form.primaryFailureCategory}
          onChange={(event) => setDraft({ ...form, primaryFailureCategory: event.target.value as '' | FailureCategory })}
        >
          <option value="">Not recorded</option>
          {form.failureCategories.map((code) => (
            <option key={code} value={code}>
              {FAILURE_CATEGORY_LABELS[code]}
            </option>
          ))}
        </select>
      </label>
      <label>
        Tags (optional)
        <input value={form.dataTags} onChange={(event) => setDraft({ ...form, dataTags: event.target.value })} autoComplete="off" maxLength={500} />
      </label>
      <label>
        Summary in your own words
        <textarea value={form.technicianSummary} onChange={(event) => setDraft({ ...form, technicianSummary: event.target.value })} rows={4} maxLength={5000} />
      </label>
      {error && <p className="error">{error}</p>}
      {record && <AuditLine record={record} />}
      <div className="row end">
        <button type="submit" className="primary" disabled={draft === null && record !== undefined}>
          Save summary
        </button>
      </div>
    </form>
  )
}
