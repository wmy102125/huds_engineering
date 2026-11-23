import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CurrentUser, FinalStatus, FixOutcome, GoogleReviewStatus, VerificationCheckCode } from '../../api/types'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalVisit, LocalVisitCompletion } from '../../db/db'
import { navigate } from '../../routes/useRoute'
import { completionFormFrom, saveVisitCompletion } from './visitCompletionStore'
import { requestAddPart } from '../cases/casePartStore'
import { transitionVisit } from './visitStore'
import type { CompletionForm } from './visitCompletionStore'
import {
  FINAL_STATUS_LABELS,
  FIX_OUTCOME_LABELS,
  GOOGLE_REVIEW_LABELS,
  labelOf,
  VERIFICATION_CHECK_CODES,
  VERIFICATION_CHECK_LABELS,
} from './visitRecordLabels'
import { visitRecordsBlockedReason } from './visitRecordGuard'

interface Props {
  visit: LocalVisit
  user: CurrentUser
  record: LocalVisitCompletion | undefined
  /** The visit has its safety checks; it cannot be completed without them. */
  hasSafetyChecks: boolean
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

/**
 * Section 5 of the form on the visit page: what was verified, the state of the appliance when the technician leaves,
 * the fix outcome and the Google review status. It is filled in and saved while the visit is on site (before the visit is
 * completed); afterwards it is only shown.
 */
export function VisitCompletionCard({ visit, user, record, hasSafetyChecks, onSaved }: Props) {
  const blocked = visitRecordsBlockedReason(visit, user)
  const refused = record?.syncState === 'REJECTED'
  const editable = blocked === null && !refused

  // What the user has typed and not saved yet. Without it the form shows the saved record.
  const [draft, setDraft] = useState<CompletionForm | null>(null)
  const [error, setError] = useState<string | null>(null)
  const form = draft ?? completionFormFrom(record)

  /** Saves what was typed, completes the visit and goes back to the case, where the summary and the bill are. */
  const handleComplete = async () => {
    setError(null)
    if (!hasSafetyChecks) {
      setError('Fill in and save the safety checks first.')
      return
    }
    // The final status decides what happens to the case when the visit is completed.
    if (form.finalStatus === '') {
      setError('Choose the final status first. It decides the next step of the case.')
      return
    }
    try {
      if (draft !== null) {
        await saveVisitCompletion(visit, user, form)
      }
      await transitionVisit(visit, user, { toState: 'COMPLETED', reason: '' })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The visit could not be completed.')
      return
    }
    setDraft(null)
    onSaved()
    // Parts are needed: open the case page with the "Add part" dialog ready.
    if (form.finalStatus === 'PARTS_REQUIRED') {
      requestAddPart(visit.diagnosticCaseId)
    }
    navigate(`/my/cases/${visit.diagnosticCaseId}`)
  }

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await saveVisitCompletion(visit, user, form)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The record could not be saved.')
      return
    }
    setDraft(null)
    onSaved()
  }

  const heading = (
    <h3>
      Completion record {record && <SyncBadge state={record.syncState} />}
    </h3>
  )

  if (!editable) {
    const ticked = record ? VERIFICATION_CHECK_CODES.filter((code) => record.verificationChecks[code]) : []
    return (
      <div className="card">
        {heading}
        {!record && <p className="muted">{blocked ?? 'Nothing recorded.'}</p>}
        {record && (
          <dl className="facts">
            <dt>Verified</dt>
            <dd>{ticked.length === 0 ? '-' : ticked.map((code) => VERIFICATION_CHECK_LABELS[code]).join(', ')}</dd>
            <dt>Final status</dt>
            <dd>{labelOf(FINAL_STATUS_LABELS, record.finalStatus)}</dd>
            <dt>Fix outcome</dt>
            <dd>{labelOf(FIX_OUTCOME_LABELS, record.fixOutcome)}</dd>
            <dt>Google review</dt>
            <dd>{labelOf(GOOGLE_REVIEW_LABELS, record.googleReviewStatus)}</dd>
          </dl>
        )}
        {record && <AuditLine record={record} />}
        {refused && <p className="notice">The server refused a change to the completion record. See "Needs attention" at the top of the page.</p>}
      </div>
    )
  }

  const toggle = (code: VerificationCheckCode, checked: boolean) =>
    setDraft({ ...form, verificationChecks: { ...form.verificationChecks, [code]: checked } })

  return (
    <form className="card stack" onSubmit={handleSubmit}>
      {heading}
      {!record && <p className="muted small">Fill in and save this before the visit is completed.</p>}
      <fieldset>
        <legend>Verified before leaving</legend>
        {VERIFICATION_CHECK_CODES.map((code) => (
          <label key={code} className="check">
            <input type="checkbox" checked={form.verificationChecks[code] === true} onChange={(event) => toggle(code, event.target.checked)} />
            {VERIFICATION_CHECK_LABELS[code]}
          </label>
        ))}
      </fieldset>
      <Choice<FinalStatus>
        label="Final status of the appliance"
        labels={FINAL_STATUS_LABELS}
        value={form.finalStatus}
        onChange={(value) => setDraft({ ...form, finalStatus: value })}
      />
      <Choice<FixOutcome>
        label="Fix outcome"
        labels={FIX_OUTCOME_LABELS}
        value={form.fixOutcome}
        onChange={(value) => setDraft({ ...form, fixOutcome: value })}
      />
      <Choice<GoogleReviewStatus>
        label="Google review"
        labels={GOOGLE_REVIEW_LABELS}
        value={form.googleReviewStatus}
        onChange={(value) => setDraft({ ...form, googleReviewStatus: value })}
      />
      {error && <p className="error">{error}</p>}
      {record && <AuditLine record={record} />}
      {!hasSafetyChecks && <p className="muted small">The visit can be completed after the safety checks are saved.</p>}
      <div className="row end">
        <button type="submit" disabled={draft === null && record !== undefined}>
          Save record
        </button>
        <button type="button" className="primary" disabled={!hasSafetyChecks} onClick={() => void handleComplete()}>
          Complete visit
        </button>
      </div>
    </form>
  )
}
