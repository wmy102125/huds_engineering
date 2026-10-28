import { useState } from 'react'
import { Pager, PAGE_SIZE } from '../../components/Pager'
import { SyncBadge } from '../../components/SyncBadge'
import { db } from '../../db/db'
import type { LocalModel } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { APPLIANCE_TYPES, applianceTypeLabel } from './applianceTypes'
import { listAppliances } from './applianceStore'
import { ModelFormModal } from './ModelFormModal'
import { listModels } from './modelStore'

interface Props {
  sync: SyncStatus
}

interface Loaded {
  models: LocalModel[]
  /** Number of appliances of every model, by model id. */
  counts: Record<string, number>
}

async function loadModels(): Promise<Loaded> {
  // Touch the database once so a closed database fails here, not in the middle of the page.
  void db()
  const counts: Record<string, number> = {}
  for (const appliance of await listAppliances()) {
    counts[appliance.applianceModelId] = (counts[appliance.applianceModelId] ?? 0) + 1
  }
  return { models: await listModels(), counts }
}

/** The appliance catalog: every model. A model opens the list of its appliances. */
export function ModelsPage({ sync }: Props) {
  const { data, reload } = useLocalQuery(loadModels, sync.tick)
  const [type, setType] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)

  const models = data?.models ?? []
  const counts = data?.counts ?? {}
  const needle = query.trim().toLowerCase()
  const matches = models.filter(
    (model) =>
      (type === '' || model.applianceType === type) &&
      (needle === '' || model.brand.toLowerCase().includes(needle) || model.modelNo.toLowerCase().includes(needle)),
  )
  const shown = matches.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <section>
      <div className="page-head">
        <h2>Appliances</h2>
        <div className="row">
          <select
            className="search"
            aria-label="Type"
            value={type}
            onChange={(event) => {
              setType(event.target.value)
              setPage(0)
            }}
          >
            <option value="">All types</option>
            {APPLIANCE_TYPES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            className="search"
            type="search"
            placeholder="Search brand or model number"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
          />
          <button type="button" className="primary" onClick={() => setFormOpen(true)}>
            + New model
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Brand</th>
              <th>Model number</th>
              <th>Appliances</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {data !== undefined && shown.length === 0 && (
              <tr>
                <td colSpan={5} className="muted">
                  {models.length === 0 ? 'No models yet.' : 'No model matches the search.'}
                </td>
              </tr>
            )}
            {shown.map((model) => (
              <tr key={model.applianceModelId}>
                <td data-label="Type">
                  <a href={`#/my/appliances/models/${model.applianceModelId}`}>
                    <strong>{applianceTypeLabel(model.applianceType)}</strong>
                  </a>
                </td>
                <td data-label="Brand">{model.brand}</td>
                <td data-label="Model number">{model.modelNo}</td>
                <td data-label="Appliances">{counts[model.applianceModelId] ?? 0}</td>
                <td data-label="Status">
                  <SyncBadge state={model.syncState} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} total={matches.length} onPage={setPage} />

      {formOpen && (
        <ModelFormModal
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false)
            void reload()
            void sync.requestSync()
          }}
        />
      )}
    </section>
  )
}
