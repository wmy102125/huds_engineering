import { useCallback, useState } from 'react'
import type { CurrentUser } from '../../api/types'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import { useLocalQuery } from '../../db/useLocalQuery'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { applianceTypeLabel } from '../appliances/applianceTypes'
import { listParts } from '../cases/casePartStore'
import { StatusBadge } from '../cases/StatusBadge'
import { AttachmentsCard } from '../attachments/AttachmentsCard'
import { listAttachments } from '../attachments/attachmentStore'
import { DiagnosticStepsCard } from './DiagnosticStepsCard'
import { SafetyCheckCard } from './SafetyCheckCard'
import { VisitCompletionCard } from './VisitCompletionCard'
import { listSteps } from './diagnosticStepStore'
import { getSafetyCheck } from './safetyCheckStore'
import { getVisitCompletion } from './visitCompletionStore'
import { VisitFormModal, VisitStepModal } from './VisitModals'
import { VisitStatusBadge } from './VisitStatusBadge'
import { formatVisitTime, visitKindLabel, visitStatusLabel } from './visitLabels'
import type { VisitStep } from './visitStateMachine'
import { canAssignVisits, getVisitDetail, visitStartBlockReason } from './visitStore'

interface Props {
  serviceVisitId: string
  user: CurrentUser
  sync: SyncStatus
}

/** One visit: where, what, who, its history, and the steps that are possible now. */
export function VisitDetailPage({ serviceVisitId, user, sync }: Props) {
  const load = useCallback(
    async () => ({
      detail: await getVisitDetail(serviceVisitId),
      safetyCheck: await getSafetyCheck(serviceVisitId),
      steps: await listSteps(serviceVisitId),
      completion: await getVisitCompletion(serviceVisitId),
      attachments: await listAttachments('SERVICE_VISIT', serviceVisitId),
      parts: await (async () => {
        const detail = await getVisitDetail(serviceVisitId)
        return detail ? listParts(detail.visit.diagnosticCaseId) : []
      })(),
    }),
    [serviceVisitId],
  )
  const { data, reload } = useLocalQuery(load, sync.tick)
  const [editing, setEditing] = useState(false)
  const [step, setStep] = useState<VisitStep | null>(null)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const detail = data.detail
  if (!detail) {
    return (
      <section>
        <a className="back" href="#/my/visits">
          ← My visits
        </a>
        <p className="muted">This visit was not found on this device.</p>
      </section>
    )
  }
  const { visit, caseRecord, customer, appliance, model, timeline, steps, otherOpenVisit } = detail
  const admin = canAssignVisits(user)
  const mine = visit.technicianId === user.userId
  const refused = visit.syncState === 'REJECTED'
  const snapshot = caseRecord?.serviceAddressSnapshot

  const saved = () => {
    setEditing(false)
    setStep(null)
    void reload()
    void sync.requestSync()
  }

  /** Why a step cannot be taken now, or null when it can. */
  const blockedReason = (candidate: VisitStep): string | null => {
    if (refused) {
      return 'The server refused a change to this visit.'
    }
    if (candidate.adminOnly) {
      return admin ? null : 'Only an administrator can do this.'
    }
    if (!mine && !admin) {
      return 'This visit is assigned to another technician.'
    }
    // The completion needs the safety checks of this visit.
    if (candidate.toState === 'COMPLETED' && !data.safetyCheck) {
      return 'Fill in and save the safety checks first.'
    }
    if (candidate.toState === 'IN_PROGRESS' && visit.visitStatus === 'PLANNED') {
      const blocked = visitStartBlockReason(caseRecord, data.parts)
      if (blocked) {
        return blocked
      }
      if (otherOpenVisit) {
        return 'Another visit of this case is still going on.'
      }
    }
    return null
  }
  const visibleSteps = steps.filter((candidate) => !candidate.adminOnly || admin)

  return (
    <section>
      <a className="back" href="#/my/visits">
        ← My visits
      </a>
      <div className="page-head">
        <h2>Visit{caseRecord?.caseNo ? ` · case ${caseRecord.caseNo}` : ''}</h2>
        <div className="row">
          <VisitStatusBadge status={visit.visitStatus} />
          <SyncBadge state={visit.syncState} />
        </div>
      </div>

      {refused && <p className="notice">The server refused a change to this visit. See "Needs attention" at the top of the page. Further changes wait until you have decided.</p>}

      <div className="card">
        <dl className="facts">
          <dt>Case</dt>
          <dd>
            {caseRecord ? (
              <>
                <a href={`#/my/cases/${caseRecord.diagnosticCaseId}`}>{caseRecord.caseNo || 'Number pending'}</a> <StatusBadge status={caseRecord.workflowStatus} />
              </>
            ) : (
              'Unknown case'
            )}
          </dd>
          <dt>Customer</dt>
          <dd>
            {customer ? <a href={`#/my/customers/${customer.customerId}`}>{customer.customerName}</a> : 'Unknown customer'}
            {customer?.phone && <span className="muted"> · {customer.phone}</span>}
          </dd>
          <dt>Address</dt>
          <dd>{snapshot ? [snapshot.addressLine1, snapshot.addressLine2, snapshot.city, snapshot.province, snapshot.postalCode].filter(Boolean).join(', ') : '-'}</dd>
          <dt>Appliance</dt>
          <dd>
            {appliance && model
              ? `${[applianceTypeLabel(model.applianceType), model.brand, model.modelNo].filter(Boolean).join(' ')}${appliance.serialNo ? ` (serial ${appliance.serialNo})` : ''}`
              : 'Unknown appliance'}
          </dd>
          <dt>Problem</dt>
          <dd>{caseRecord?.reportedSymptoms ?? '-'}</dd>
          <dt>Kind</dt>
          <dd>{visitKindLabel(visit.visitKind)}</dd>
          <dt>Technician</dt>
          <dd>{visit.technicianName || '-'}</dd>
          <dt>Planned for</dt>
          <dd>{formatVisitTime(visit.scheduledAt)}</dd>
          {visit.startedAt && (
            <>
              <dt>Started</dt>
              <dd>{formatVisitTime(visit.startedAt)}</dd>
            </>
          )}
          {visit.endedAt && (
            <>
              <dt>Ended</dt>
              <dd>{formatVisitTime(visit.endedAt)}</dd>
            </>
          )}
          <dt>Notes</dt>
          <dd>{visit.notes || '-'}</dd>
        </dl>
        <AuditLine record={visit} />
        {admin && visit.visitStatus === 'PLANNED' && (
          <div className="row">
            <button type="button" disabled={refused} onClick={() => setEditing(true)}>
              Change technician or time
            </button>
          </div>
        )}
      </div>

      {visibleSteps.length > 0 && (
        <div className="card">
          <h3>Next step</h3>
          {caseRecord?.workflowStatus === 'OPEN' && visit.visitStatus === 'PLANNED' && (
            <p className="muted small">Starting the visit also moves the case to "Diagnosing".</p>
          )}
          <div className="row">
            {visibleSteps.map((candidate) => {
              const blocked = blockedReason(candidate)
              return (
                <button
                  key={candidate.toState}
                  type="button"
                  className={candidate.needsReason || candidate.adminOnly || candidate.toState === 'COMPLETED' ? '' : 'primary'}
                  disabled={blocked !== null}
                  title={blocked ?? undefined}
                  onClick={() => setStep(candidate)}
                >
                  {candidate.label}
                </button>
              )
            })}
          </div>
          {visibleSteps.map((candidate) => {
            const blocked = blockedReason(candidate)
            return blocked && !refused ? (
              <p key={candidate.toState} className="muted small">
                {candidate.label}: {blocked}
              </p>
            ) : null
          })}
        </div>
      )}


      {/* Shown from the moment the visit is on site; a planned visit has nothing recorded yet. */}
      {visit.visitStatus !== 'PLANNED' && (
        <>
          <SafetyCheckCard key={visit.serviceVisitId} visit={visit} user={user} record={data.safetyCheck} onSaved={saved} />
          <DiagnosticStepsCard visit={visit} user={user} steps={data.steps} onSaved={saved} />
          <VisitCompletionCard key={`completion-${visit.serviceVisitId}`} visit={visit} user={user} record={data.completion} hasSafetyChecks={data.safetyCheck !== undefined} onSaved={saved} />
        </>
      )}

      <AttachmentsCard
        entityType="SERVICE_VISIT"
        entityId={visit.serviceVisitId}
        user={user}
        rows={data.attachments}
        onChanged={saved}
        readOnly={visit.visitStatus === 'COMPLETED' || visit.visitStatus === 'CANCELLED'}
      />

      <h3>History</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Step</th>
              <th>Who</th>
              <th>Note</th>
            </tr>
          </thead>
          <tbody>
            {timeline.length === 0 && (
              <tr>
                <td colSpan={4} className="muted">
                  The history has not been downloaded yet.
                </td>
              </tr>
            )}
            {timeline.map((entry) => (
              <tr key={entry.key}>
                <td data-label="When">{new Date(entry.at).toLocaleString()}</td>
                <td data-label="Step">
                  {entry.fromState ? `${visitStatusLabel(entry.fromState)} → ` : 'Created → '}
                  <strong>{visitStatusLabel(entry.toState)}</strong>
                  {entry.waiting && <span className="badge wait"> Saved on this device</span>}
                </td>
                <td data-label="Who">{entry.actorName}</td>
                <td data-label="Note">{entry.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && <VisitFormModal user={user} visit={visit} onClose={() => setEditing(false)} onSaved={saved} />}
      {step && <VisitStepModal user={user} visit={visit} step={step} onClose={() => setStep(null)} onSaved={saved} />}
    </section>
  )
}
