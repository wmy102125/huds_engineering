import { useCallback, useState } from 'react'
import type { CurrentUser } from '../../api/types'
import { SyncBadge } from '../../components/SyncBadge'
import { useLocalQuery } from '../../db/useLocalQuery'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { VisitStatusBadge } from './VisitStatusBadge'
import { formatVisitTime, isVisitFinished, isVisitOpen } from './visitLabels'
import { canAssignVisits, listVisitRows } from './visitStore'
import type { VisitRow } from './visitStore'

interface Props {
  user: CurrentUser
  sync: SyncStatus
}

/** The visits of the logged-in user. Administrators can switch to everybody's visits. */
export function MyVisitsPage({ user, sync }: Props) {
  const admin = canAssignVisits(user)
  const [everyone, setEveryone] = useState(false)
  const load = useCallback(() => listVisitRows(everyone ? null : user.userId), [everyone, user.userId])
  const { data } = useLocalQuery(load, sync.tick)

  const rows = data ?? []
  const onSite = rows.filter((row) => isVisitOpen(row.visit.visitStatus))
  const planned = rows.filter((row) => row.visit.visitStatus === 'PLANNED')
  const finished = rows.filter((row) => isVisitFinished(row.visit.visitStatus)).slice(0, 20)

  return (
    <section>
      <div className="page-head">
        <h2>{everyone ? 'All visits' : 'My visits'}</h2>
        {admin && (
          <label className="check">
            <input type="checkbox" checked={everyone} onChange={(event) => setEveryone(event.target.checked)} />
            Show everybody's visits
          </label>
        )}
      </div>
      {data !== undefined && rows.length === 0 && <p className="muted">No visits yet. An administrator plans a visit from the case page.</p>}
      <VisitTable title="On site now" rows={onSite} showTechnician={everyone} />
      <VisitTable title="Planned" rows={planned} showTechnician={everyone} />
      <VisitTable title="Recently finished" rows={finished} showTechnician={everyone} />
    </section>
  )
}

function VisitTable({ title, rows, showTechnician }: { title: string; rows: VisitRow[]; showTechnician: boolean }) {
  if (rows.length === 0) {
    return null
  }
  return (
    <>
      <h3>{title}</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Case</th>
              <th>Customer</th>
              <th>Appliance</th>
              {showTechnician && <th>Technician</th>}
              <th>State</th>
              <th>Sync</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.visit.serviceVisitId}>
                <td data-label="When">
                  <a href={`#/my/visits/${row.visit.serviceVisitId}`}>{formatVisitTime(row.visit.startedAt ?? row.visit.scheduledAt)}</a>
                </td>
                <td data-label="Case">{row.caseNo || 'Number pending'}</td>
                <td data-label="Customer">{row.customerName}</td>
                <td data-label="Appliance">{row.applianceText}</td>
                {showTechnician && <td data-label="Technician">{row.visit.technicianName}</td>}
                <td data-label="State">
                  <VisitStatusBadge status={row.visit.visitStatus} />
                </td>
                <td data-label="Sync">
                  <SyncBadge state={row.visit.syncState} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
