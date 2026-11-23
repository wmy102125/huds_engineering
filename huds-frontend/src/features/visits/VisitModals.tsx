import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CurrentUser, VisitKind } from '../../api/types'
import type { LocalCase, LocalVisit } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import { Modal } from '../my/Modal'
import { toDateTimeInput, VISIT_KIND_LABELS, visitStatusLabel } from './visitLabels'
import type { VisitStep } from './visitStateMachine'
import { canAssignVisits, listTechnicians, planVisit, transitionVisit, updateVisit } from './visitStore'

interface FormProps {
  user: CurrentUser
  /** Set when a new visit is planned for this case. */
  caseRecord?: LocalCase
  /** Set when a planned visit is changed. */
  visit?: LocalVisit
  onClose: () => void
  onSaved: () => void
}

/** Plan a visit, or change the technician, the time and the notes of a planned visit. Administrators only. */
export function VisitFormModal({ user, caseRecord, visit, onClose, onSaved }: FormProps) {
  const { data: technicians } = useLocalQuery(listTechnicians, 0)
  // A technician plans a follow-up visit for himself or herself: the technician and the kind are fixed.
  const admin = canAssignVisits(user)
  const [technicianId, setTechnicianId] = useState(visit?.technicianId ?? (admin ? '' : user.userId))
  const [visitKind, setVisitKind] = useState<VisitKind | ''>(visit?.visitKind ?? (admin ? '' : 'FOLLOW_UP'))
  const [scheduledAt, setScheduledAt] = useState(toDateTimeInput(visit?.scheduledAt ?? null))
  const [notes, setNotes] = useState(visit?.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      const form = { technicianId, visitKind, scheduledAt, notes }
      if (visit) {
        await updateVisit(visit, user, form)
      } else if (caseRecord) {
        await planVisit(caseRecord, user, form)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The visit could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={visit ? 'Change planned visit' : 'Plan a visit'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        {admin ? (
          <>
          <label>
            Technician
            <select value={technicianId} onChange={(event) => setTechnicianId(event.target.value)} autoFocus>
              <option value="">Choose...</option>
              {(technicians ?? []).map((technician) => (
                <option key={technician.userId} value={technician.userId}>
                  {technician.userName}
                </option>
              ))}
            </select>
          </label>
          <label>
            Kind of visit
            <select value={visitKind} onChange={(event) => setVisitKind(event.target.value as VisitKind | '')}>
              <option value="">Let the system decide (first visit, then follow-up)</option>
              {(Object.keys(VISIT_KIND_LABELS) as VisitKind[]).map((kind) => (
                <option key={kind} value={kind}>
                  {VISIT_KIND_LABELS[kind]}
                </option>
              ))}
            </select>
          </label>
          </>
        ) : (
          <p className="muted">
            Follow-up visit for you ({user.userName}). Only an administrator can plan a visit for someone else.
          </p>
        )}
        <label>
          Planned time (optional)
          <input type="datetime-local" value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} />
        </label>
        <label>
          Notes for the technician (optional)
          <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={3} />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            {visit ? 'Save changes' : 'Plan visit'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface StepProps {
  user: CurrentUser
  visit: LocalVisit
  step: VisitStep
  onClose: () => void
  onSaved: () => void
}

/** Asks for what the step needs (a reason for a pause) and records the step. */
export function VisitStepModal({ user, visit, step, onClose, onSaved }: StepProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await transitionVisit(visit, user, { toState: step.toState, reason })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The step could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={step.label} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <p className="muted">
          {visitStatusLabel(visit.visitStatus)} → {visitStatusLabel(step.toState)}
        </p>
        <label>
          {step.needsReason ? 'Reason' : 'Note (optional)'}
          <textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} autoFocus />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Back
          </button>
          <button type="submit" className="primary">
            Confirm
          </button>
        </div>
      </form>
    </Modal>
  )
}
