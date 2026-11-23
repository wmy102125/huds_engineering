import { useCallback, useState } from 'react'
import type { CurrentUser } from '../../api/types'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import { useLocalQuery } from '../../db/useLocalQuery'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { applianceTypeLabel } from '../appliances/applianceTypes'
import { AttachmentsCard } from '../attachments/AttachmentsCard'
import { listAttachments } from '../attachments/attachmentStore'
import { CaseVisits } from '../visits/CaseVisits'
import { findOpenVisit, listVisitsOfCase } from '../visits/visitStore'
import { CaseTextModal, TransitionModal } from './CaseModals'
import { dispositionLabel, isFinished, statusLabel, symptomLabel, timingText } from './caseLabels'
import { listErrorCodes } from './caseErrorCodeStore'
import { isPartWaiting, listParts } from './casePartStore'
import { CasePartsCard } from './CasePartsCard'
import { CaseSummaryCard } from './CaseSummaryCard'
import { ErrorCodesCard } from './ErrorCodesCard'
import { InvoiceCard } from './InvoiceCard'
import { getCaseSummary } from './caseSummaryStore'
import { getInvoice, listPayments } from './invoiceStore'
import { getCaseDetail } from './caseStore'
import { optionsForUser } from './caseStateMachine'
import type { TransitionOption } from './caseStateMachine'
import { StatusBadge } from './StatusBadge'

interface Props {
  diagnosticCaseId: string
  user: CurrentUser
  sync: SyncStatus
}

/** The bill of the case and its payments. */
async function loadInvoice(diagnosticCaseId: string) {
  const invoice = await getInvoice(diagnosticCaseId)
  return { invoice, payments: invoice ? await listPayments(invoice.caseInvoiceId) : [] }
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString()
}

/** One case: who, where, what, its history, and the steps that are possible now. */
export function CaseDetailPage({ diagnosticCaseId, user, sync }: Props) {
  const load = useCallback(
    async () => ({
      detail: await getCaseDetail(diagnosticCaseId),
      visits: await listVisitsOfCase(diagnosticCaseId),
      codes: await listErrorCodes(diagnosticCaseId),
      parts: await listParts(diagnosticCaseId),
      summary: await getCaseSummary(diagnosticCaseId),
      attachments: await listAttachments('CASE', diagnosticCaseId),
      ...(await loadInvoice(diagnosticCaseId)),
    }),
    [diagnosticCaseId],
  )
  const { data, reload } = useLocalQuery(load, sync.tick)
  const [editing, setEditing] = useState(false)
  const [step, setStep] = useState<TransitionOption | null>(null)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const detail = data.detail
  if (!detail) {
    return (
      <section>
        <a className="back" href="#/my/cases">
          ← Cases
        </a>
        <p className="muted">This case was not found on this device.</p>
      </section>
    )
  }
  const { caseRecord, customer, address, appliance, model, timeline, resumeTo } = detail
  const visits = data.visits
  // While a technician is on site (or paused) the case cannot change, except between "In repair" and "Verifying".
  const visitOpen = findOpenVisit(visits) !== undefined
  // The repair cannot start while a part is still on its way.
  const waitingParts = data.parts.filter((part) => isPartWaiting(part)).length
  // After the customer approved, "wait for parts" only makes sense when a part is on order: the parts are listed first.
  const hidesWaitForParts = caseRecord.workflowStatus === 'PENDING_AUTHORIZATION' && waitingParts === 0
  const options = optionsForUser(detail.options, user, visitOpen).filter(
    (option) => !(hidesWaitForParts && option.toState === 'PENDING_PARTS'),
  )
  const blockedByParts = (option: TransitionOption) => caseRecord.workflowStatus === 'PENDING_PARTS' && option.toState === 'IN_REPAIR' && waitingParts > 0
  const finished = isFinished(caseRecord.workflowStatus)
  const refused = caseRecord.syncState === 'REJECTED'
  const snapshot = caseRecord.serviceAddressSnapshot

  const saved = () => {
    setEditing(false)
    setStep(null)
    void reload()
    void sync.requestSync()
  }

  return (
    <section>
      <a className="back" href="#/my/cases">
        ← Cases
      </a>
      <div className="page-head">
        <h2>Case {caseRecord.caseNo || '(number pending)'}</h2>
        <div className="row">
          <StatusBadge status={caseRecord.workflowStatus} />
          <SyncBadge state={caseRecord.syncState} />
        </div>
      </div>

      {refused && <p className="notice">The server refused a change to this case. See "Needs attention" at the top of the page. Further changes wait until you have decided.</p>}

      <div className="card">
        <dl className="facts">
          <dt>Customer</dt>
          <dd>
            {customer ? <a href={`#/my/customers/${customer.customerId}`}>{customer.customerName}</a> : 'Unknown customer'}
            {customer?.phone && <span className="muted"> · {customer.phone}</span>}
          </dd>
          <dt>Address</dt>
          <dd>
            {[snapshot.addressLine1, snapshot.addressLine2, snapshot.city, snapshot.province, snapshot.postalCode].filter(Boolean).join(', ') ||
              (address ? address.addressLine1 : '-')}
          </dd>
          <dt>Appliance</dt>
          <dd>
            {appliance && model ? (
              <a href={`#/my/appliances/items/${appliance.applianceId}`}>
                {[applianceTypeLabel(model.applianceType), model.brand, model.modelNo].filter(Boolean).join(' ')}
                {appliance.serialNo ? ` (serial ${appliance.serialNo})` : ''}
              </a>
            ) : (
              'Unknown appliance'
            )}
          </dd>
          <dt>Problem</dt>
          <dd>{caseRecord.reportedSymptoms}</dd>
          <dt>Main symptom</dt>
          <dd>{symptomLabel(caseRecord.primarySymptom)}</dd>
          <dt>Other symptoms</dt>
          <dd>{caseRecord.secondarySymptoms.map((item) => symptomLabel(item)).join(', ') || '-'}</dd>
          <dt>When it shows</dt>
          <dd>{timingText(caseRecord.failureTiming)}</dd>
          {caseRecord.workflowStatus === 'ON_HOLD' && (
            <>
              <dt>On hold because</dt>
              <dd>{caseRecord.holdReason}</dd>
            </>
          )}
          {finished && (
            <>
              <dt>Result</dt>
              <dd>{dispositionLabel(caseRecord.disposition)}</dd>
            </>
          )}
          <dt>Opened</dt>
          <dd>{formatTime(caseRecord.openedAt)}</dd>
          {caseRecord.closedAt && (
            <>
              <dt>Ended</dt>
              <dd>{formatTime(caseRecord.closedAt)}</dd>
            </>
          )}
        </dl>
        <AuditLine record={caseRecord} />
        {!finished && (
          <div className="row">
            <button type="button" disabled={refused} onClick={() => setEditing(true)}>
              Edit problem
            </button>
          </div>
        )}
      </div>

      {/* Photos and videos of the problem come right after the description. */}
      <AttachmentsCard
        entityType="CASE"
        entityId={caseRecord.diagnosticCaseId}
        user={user}
        rows={data.attachments}
        onChanged={saved}
        readOnly={finished}
      />

      <ErrorCodesCard caseRecord={caseRecord} codes={data.codes} editable={!finished && !refused} onSaved={saved} />

      <CaseVisits caseRecord={caseRecord} user={user} visits={visits} onSaved={saved} />

      <CasePartsCard caseRecord={caseRecord} parts={data.parts} editable={!finished && !refused} onSaved={saved} />

      {/* The summary comes after the work: it is shown once a visit is completed (or when it was written already). */}
      {(data.summary !== undefined || visits.some((visit) => visit.visitStatus === 'COMPLETED')) && (
        <CaseSummaryCard key={`summary-${caseRecord.diagnosticCaseId}`} caseRecord={caseRecord} user={user} record={data.summary} onSaved={saved} />
      )}

      <InvoiceCard caseRecord={caseRecord} user={user} invoice={data.invoice} payments={data.payments} onSaved={saved} />

      {!finished && (
        <div className="card">
          <h3>Next step</h3>
          {visitOpen && <p className="muted small">A visit is going on. The case moves on when the visit is completed.</p>}
          <div className="row">
            {options.map((option) => (
              <button
                key={`${option.toState}-${option.label}`}
                type="button"
                className={option.needsReason || option.dispositions ? '' : 'primary'}
                disabled={refused || blockedByParts(option)}
                onClick={() => setStep(option)}
              >
                {option.label}
              </button>
            ))}
          </div>
          {options.some(blockedByParts) && (
            <p className="muted small">{waitingParts} part(s) have not arrived yet. Mark them as arrived to start the repair.</p>
          )}
          {caseRecord.workflowStatus === 'PENDING_PARTS' && data.parts.length > 0 && waitingParts === 0 && (
            <p className="muted small">All parts have arrived. Plan a visit; starting it moves the case to "In repair".</p>
          )}
          {hidesWaitForParts && !visitOpen && detail.options.some((option) => option.toState === 'PENDING_PARTS') && (
            <p className="muted small">
              Parts needed? Add them in the Parts card as Ordered or Backorder, then "Customer approved: wait for parts" appears.
            </p>
          )}
          {!visitOpen && options.length === 0 && <p className="muted small">There is no step you can take on this case.</p>}
          {caseRecord.workflowStatus === 'ON_HOLD' && resumeTo === null && (
            <p className="muted small">The history of this case is still being downloaded; resuming is possible after that.</p>
          )}
        </div>
      )}

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
                <td data-label="When">{formatTime(entry.at)}</td>
                <td data-label="Step">
                  {entry.fromState ? `${statusLabel(entry.fromState)} → ` : 'Opened → '}
                  <strong>{statusLabel(entry.toState)}</strong>
                  {entry.waiting && <span className="badge wait"> Saved on this device</span>}
                </td>
                <td data-label="Who">{entry.actorName}</td>
                <td data-label="Note">{entry.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && <CaseTextModal caseRecord={caseRecord} onClose={() => setEditing(false)} onSaved={saved} />}
      {step && <TransitionModal caseRecord={caseRecord} resumeTo={resumeTo} option={step} onClose={() => setStep(null)} onSaved={saved} />}
    </section>
  )
}
