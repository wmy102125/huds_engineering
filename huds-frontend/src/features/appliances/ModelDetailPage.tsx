import { useCallback, useState } from 'react'
import { AuditLine } from '../../components/AuditLine'
import { Pager, PAGE_SIZE } from '../../components/Pager'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalAppliance, LocalModel } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import { navigate } from '../../routes/useRoute'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { ApplianceFormModal } from './ApplianceFormModal'
import { applianceTypeLabel } from './applianceTypes'
import { listAppliancesOfModel } from './applianceStore'
import { ModelFormModal } from './ModelFormModal'
import { archiveModel, getModel } from './modelStore'

interface Props {
  applianceModelId: string
  sync: SyncStatus
}

interface Loaded {
  model: LocalModel | undefined
  appliances: LocalAppliance[]
}

/** One model on top, the appliances of this model below. */
export function ModelDetailPage({ applianceModelId, sync }: Props) {
  const load = useCallback(
    async (): Promise<Loaded> => ({
      model: await getModel(applianceModelId),
      appliances: await listAppliancesOfModel(applianceModelId),
    }),
    [applianceModelId],
  )
  const { data, reload } = useLocalQuery(load, sync.tick)
  const [editingModel, setEditingModel] = useState(false)
  const [applianceFormOpen, setApplianceFormOpen] = useState(false)
  const [page, setPage] = useState(0)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const { model, appliances } = data
  if (!model || model.archiving) {
    return (
      <section>
        <a className="back" href="#/my/appliances">
          ← Appliances
        </a>
        <p className="muted">This model was not found on this device.</p>
      </section>
    )
  }

  const saved = () => {
    void reload()
    void sync.requestSync()
  }

  const handleArchive = async () => {
    if (!window.confirm('Archive this model? The server refuses it while appliances still use the model.')) {
      return
    }
    try {
      await archiveModel(model)
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'The model could not be archived.')
      return
    }
    void sync.requestSync()
    navigate('/my/appliances')
  }

  const shown = appliances.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <section>
      <a className="back" href="#/my/appliances">
        ← Appliances
      </a>
      <div className="page-head">
        <h2>
          {applianceTypeLabel(model.applianceType)} {model.brand} {model.modelNo}
        </h2>
        <div className="row">
          <button type="button" onClick={() => setEditingModel(true)}>
            Edit
          </button>
          <button type="button" onClick={() => void handleArchive()}>
            Archive
          </button>
        </div>
      </div>

      <div className="card">
        <dl className="facts">
          <dt>Type</dt>
          <dd>{applianceTypeLabel(model.applianceType)}</dd>
          <dt>Brand</dt>
          <dd>{model.brand || '-'}</dd>
          <dt>Model number</dt>
          <dd>{model.modelNo || '-'}</dd>
          <dt>Status</dt>
          <dd>
            <SyncBadge state={model.syncState} />
          </dd>
        </dl>
        <AuditLine record={model} />
      </div>

      <div className="page-head">
        <h3>Appliances of this model</h3>
        <button type="button" className="primary" onClick={() => setApplianceFormOpen(true)}>
          + New appliance
        </button>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Serial number</th>
              <th>Serial checked</th>
              <th>Notes</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {appliances.length === 0 && (
              <tr>
                <td colSpan={4} className="muted">
                  No appliances yet.
                </td>
              </tr>
            )}
            {shown.map((appliance) => (
              <tr key={appliance.applianceId}>
                <td data-label="Serial number">
                  <a href={`#/my/appliances/items/${appliance.applianceId}`}>
                    <strong>{appliance.serialNo || '(no serial number)'}</strong>
                  </a>
                </td>
                <td data-label="Serial checked">{appliance.serialVerified ? 'Yes' : 'No'}</td>
                <td data-label="Notes">{appliance.identityNotes}</td>
                <td data-label="Status">
                  <SyncBadge state={appliance.syncState} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} total={appliances.length} onPage={setPage} />

      {editingModel && (
        <ModelFormModal
          model={model}
          onClose={() => setEditingModel(false)}
          onSaved={() => {
            setEditingModel(false)
            saved()
          }}
        />
      )}
      {applianceFormOpen && (
        <ApplianceFormModal
          applianceModelId={applianceModelId}
          onClose={() => setApplianceFormOpen(false)}
          onSaved={() => {
            setApplianceFormOpen(false)
            saved()
          }}
        />
      )}
    </section>
  )
}
