import { useState } from 'react'
import type { CurrentUser } from '../../api/types'
import type { LocalCase, LocalVisit } from '../../db/db'
import { isFinished } from '../cases/caseLabels'
import { VisitFormModal } from './VisitModals'
import { VisitStatusBadge } from './VisitStatusBadge'
import { formatVisitTime } from './visitLabels'
import { canPlanVisit, findUnfinishedVisit } from './visitStore'

interface Props {
  caseRecord: LocalCase
  user: CurrentUser
  /** The visits of the case, in the order they should be shown. */
  visits: LocalVisit[]
  onSaved: () => void
}

/** The visits of one case, with the button that plans a new one (administrators, and the technician who completed a visit of the case). */
export function CaseVisits({ caseRecord, user, visits, onSaved }: Props) {
  const [planning, setPlanning] = useState(false)
  const caseOpen = !isFinished(caseRecord.workflowStatus)
  // One visit at a time: a new one can only be planned when every earlier visit is completed or cancelled.
  const unfinished = findUnfinishedVisit(visits)
  const mayPlan = canPlanVisit(user, visits)
  // A case on hold gets no new visit until it is resumed.
  const onHold = caseRecord.workflowStatus === 'ON_HOLD'
  const canPlan = mayPlan && caseOpen && !onHold && unfinished === undefined

  return (
    <div className="card">
      <div className="row between">
        <h3>Visits</h3>
        {canPlan && (
          <button type="button" onClick={() => setPlanning(true)}>
            + Plan a visit
          </button>
        )}
      </div>
      {visits.length === 0 && <p className="muted">No visit yet.</p>}
      {mayPlan && caseOpen && onHold && <p className="muted small">The case is on hold. Resume it to plan a visit.</p>}
      {mayPlan && caseOpen && unfinished && (
        <p className="muted small">A new visit can be planned after the current one is completed or cancelled.</p>
      )}
      {visits.map((visit) => (
        <div key={visit.serviceVisitId} className="row between">
          <span>
            <a href={`#/my/visits/${visit.serviceVisitId}`}>{formatVisitTime(visit.startedAt ?? visit.scheduledAt)}</a>
            <span className="muted"> · {visit.technicianName || 'no technician'}</span>
          </span>
          <VisitStatusBadge status={visit.visitStatus} />
        </div>
      ))}
      {planning && (
        <VisitFormModal
          user={user}
          caseRecord={caseRecord}
          onClose={() => setPlanning(false)}
          onSaved={() => {
            setPlanning(false)
            onSaved()
          }}
        />
      )}
    </div>
  )
}
