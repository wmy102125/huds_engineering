import { useCallback, useState } from 'react'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalAppliance, LocalModel } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import { navigate } from '../../routes/useRoute'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { ApplianceFormModal } from './ApplianceFormModal'
import { applianceTypeLabel } from './applianceTypes'
import { archiveAppliance, getAppliance } from './applianceStore'
import { getModel } from './modelStore'

interface Props {
  applianceId: string
  sync: SyncStatus
}

interface Loaded {
  appliance: LocalAppliance | undefined
  model: LocalModel | undefined
}

/** One appliance with the details of its model. */
export function ApplianceDetailPage({ applianceId, sync }: Props) {
  const load = useCallback(async (): Promise<Loaded> => {
    const appliance = await getAppliance(applianceId)
    return { appliance, model: appliance ? await getModel(appliance.applianceModelId) : undefined }
  }, [applianceId])
  const { data, reload } = useLocalQuery(load, sync.tick)
  const [editing, setEditing] = useState(false)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const { appliance, model } = data
  if (!appliance || appliance.archiving) {
    return (
      <section>
        <a className="back" href="#/my/appliances">
          ← Appliances
        </a>
        <p className="muted">This appliance was not found on this device.</p>
      </section>
    )
  }

  const modelAddress = `#/my/appliances/models/${appliance.applianceModelId}`

  const handleArchive = async () => {
    if (!window.confirm('Archive this appliance?')) {
      return
    }
    try {
      await archiveAppliance(appliance)
    } catch (error) {
      window.alert(error instanceof Error ? error.message : 'The appliance could not be archived.')
      return
    }
    void sync.requestSync()
    navigate(modelAddress.slice(1))
  }

  return (
    <section>
      <a className="back" href={modelAddress}>
        ← {model ? `${applianceTypeLabel(model.applianceType)} ${model.brand} ${model.modelNo}` : 'Model'}
      </a>
      <div className="page-head">
        <h2>{appliance.serialNo || '(no serial number)'}</h2>
        <div className="row">
          <button type="button" onClick={() => setEditing(true)}>
            Edit
          </button>
          <button type="button" onClick={() => void handleArchive()}>
            Archive
          </button>
        </div>
      </div>

      <div className="card">
        <dl className="facts">
          <dt>Model</dt>
          <dd>
            {model ? (
              <a href={modelAddress}>
                {applianceTypeLabel(model.applianceType)} {model.brand} {model.modelNo}
              </a>
            ) : (
              '-'
            )}
          </dd>
          <dt>Serial number</dt>
          <dd>{appliance.serialNo || '-'}</dd>
          <dt>Serial checked</dt>
          <dd>{appliance.serialVerified ? 'Yes' : 'No'}</dd>
          <dt>Notes</dt>
          <dd>{appliance.identityNotes || '-'}</dd>
          <dt>Status</dt>
          <dd>
            <SyncBadge state={appliance.syncState} />
          </dd>
        </dl>
        <AuditLine record={appliance} />
      </div>

      {editing && (
        <ApplianceFormModal
          applianceModelId={appliance.applianceModelId}
          appliance={appliance}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false)
            void reload()
            void sync.requestSync()
          }}
        />
      )}
    </section>
  )
}
