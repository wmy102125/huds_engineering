import { useState } from 'react'
import { Pager, PAGE_SIZE } from '../../components/Pager'
import { SyncBadge } from '../../components/SyncBadge'
import { useLocalQuery } from '../../db/useLocalQuery'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { isFinished, STATUS_LABELS, symptomLabel } from './caseLabels'
import { listCaseRows } from './caseStore'
import { StatusBadge } from './StatusBadge'

interface Props {
  sync: SyncStatus
}

/** "unfinished" and "all" are filters of their own; the rest are the states of a case. */
const FILTER_ALL = 'all'
const FILTER_UNFINISHED = 'unfinished'

export function CasesPage({ sync }: Props) {
  const { data } = useLocalQuery(listCaseRows, sync.tick)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState(FILTER_ALL)
  const [page, setPage] = useState(0)

  const rows = data ?? []
  const needle = query.trim().toLowerCase()
  const matches = rows.filter((row) => {
    const status = row.caseRecord.workflowStatus
    if (filter === FILTER_UNFINISHED && isFinished(status)) {
      return false
    }
    if (filter !== FILTER_ALL && filter !== FILTER_UNFINISHED && status !== filter) {
      return false
    }
    if (needle === '') {
      return true
    }
    const text = [
      row.caseRecord.caseNo,
      row.customerName,
      row.customerPhone,
      row.applianceText,
      row.caseRecord.reportedSymptoms,
      symptomLabel(row.caseRecord.primarySymptom),
    ]
      .join(' ')
      .toLowerCase()
    return text.includes(needle)
  })
  const shown = matches.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <section>
      <div className="page-head">
        <h2>Cases</h2>
        <div className="row">
          <select
            value={filter}
            onChange={(event) => {
              setFilter(event.target.value)
              setPage(0)
            }}
          >
            <option value={FILTER_ALL}>All cases</option>
            <option value={FILTER_UNFINISHED}>Unfinished</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <input
            className="search"
            type="search"
            placeholder="Search number, customer, phone, appliance"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
          />
          <a className="button primary" href="#/my/cases/new">
            + New case
          </a>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Case</th>
              <th>Customer</th>
              <th>Appliance</th>
              <th>Opened</th>
              <th>State</th>
              <th>Sync</th>
            </tr>
          </thead>
          <tbody>
            {data !== undefined && shown.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  {rows.length === 0 ? 'No cases yet.' : 'No case matches.'}
                </td>
              </tr>
            )}
            {shown.map((row) => (
              <tr key={row.caseRecord.diagnosticCaseId}>
                <td data-label="Case">
                  <a href={`#/my/cases/${row.caseRecord.diagnosticCaseId}`}>
                    <strong>{row.caseRecord.caseNo || 'Number pending'}</strong>
                  </a>
                </td>
                <td data-label="Customer">
                  {row.customerName}
                  {row.customerPhone && <span className="muted small"> · {row.customerPhone}</span>}
                </td>
                <td data-label="Appliance">{row.applianceText}</td>
                <td data-label="Opened">{new Date(row.caseRecord.openedAt).toLocaleDateString()}</td>
                <td data-label="State">
                  <StatusBadge status={row.caseRecord.workflowStatus} />
                  {row.caseRecord.workflowStatus === 'ON_HOLD' && row.caseRecord.holdReason && (
                    <div className="muted small">{row.caseRecord.holdReason}</div>
                  )}
                </td>
                <td data-label="Sync">
                  <SyncBadge state={row.caseRecord.syncState} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} total={matches.length} onPage={setPage} />
    </section>
  )
}
