import { useCallback, useState } from 'react'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalAppliance, LocalCase, LocalCustomer, LocalModel } from '../../db/db'
import { useLocalQuery } from '../../db/useLocalQuery'
import { navigate } from '../../routes/useRoute'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { ApplianceFormModal } from './ApplianceFormModal'
import { applianceTypeLabel } from './applianceTypes'
import { archiveAppliance, getAppliance } from './applianceStore'
import { StatusBadge } from '../cases/StatusBadge'
import { symptomLabel } from '../cases/caseLabels'
import { listCasesOfAppliance } from '../cases/caseStore'
import { getCustomer } from '../customers/customerStore'
import { getModel } from './modelStore'
import { getDocumentRow, openDocument } from './documentStore'
import type { DocumentRow } from './documentStore'

interface Props {
  applianceId: string
  sync: SyncStatus
}

interface Loaded {
  appliance: LocalAppliance | undefined
  model: LocalModel | undefined
  customer: LocalCustomer | undefined
  cases: LocalCase[]
  /** The schematic of the appliance's model, when it has one. */
  schematic: DocumentRow | undefined
}

/** One appliance with the details of its model. */
export function ApplianceDetailPage({ applianceId, sync }: Props) {
  const load = useCallback(async (): Promise<Loaded> => {
    const appliance = await getAppliance(applianceId)
    const model = appliance ? await getModel(appliance.applianceModelId) : undefined
    return {
      appliance,
      model,
      schematic: await getDocumentRow(model?.schematicDocumentId ?? null),
      customer: appliance ? await getCustomer(appliance.customerId) : undefined,
      cases: appliance ? await listCasesOfAppliance(appliance.applianceId) : [],
    }
  }, [applianceId])
  const { data, reload } = useLocalQuery(load, sync.tick)
  const [editing, setEditing] = useState(false)
  const [schematicError, setSchematicError] = useState<string | null>(null)

  if (data === undefined) {
    return <p className="muted">Loading...</p>
  }
  const { appliance, model, customer, cases, schematic } = data
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
          <dt>Customer</dt>
          <dd>{customer ? <a href={`#/my/customers/${customer.customerId}`}>{customer.customerName}</a> : '-'}</dd>
          <dt>Serial number</dt>
          <dd>{appliance.serialNo || '-'}</dd>
          <dt>Serial checked</dt>
          <dd>{appliance.serialVerified ? 'Yes' : 'No'}</dd>
          <dt>Notes</dt>
          <dd>{appliance.identityNotes || '-'}</dd>
          <dt>Customer's asset number</dt>
          <dd>{appliance.customerAssetRef || '-'}</dd>
          <dt>Purchase order</dt>
          <dd>{appliance.purchaseOrderNumber || '-'}</dd>
          <dt>Production date</dt>
          <dd>{appliance.productionDate || '-'}</dd>
          <dt>Estimated age</dt>
          <dd>{appliance.estimatedAgeYears === null ? '-' : `${appliance.estimatedAgeYears} years`}</dd>
          <dt>Wiring diagram</dt>
          <dd>
            {model?.schematicLocation || '-'}
            {schematic &&
              (schematic.uploadStatus === 'UPLOADED' || schematic.hasLocalFile ? (
                <>
                  {' · '}
                  <a
                    href="#schematic"
                    onClick={(event) => {
                      event.preventDefault()
                      setSchematicError(null)
                      openDocument(schematic).catch((caught: unknown) =>
                        setSchematicError(caught instanceof Error ? caught.message : 'The schematic could not be opened.'),
                      )
                    }}
                  >
                    View schematic ({schematic.documentRef})
                  </a>
                </>
              ) : (
                <span className="muted"> · schematic {schematic.documentRef} has no file yet</span>
              ))}
            {schematicError && <span className="error"> {schematicError}</span>}
          </dd>
          <dt>Status</dt>
          <dd>
            <SyncBadge state={appliance.syncState} />
          </dd>
        </dl>
        <AuditLine record={appliance} />
      </div>

      <div className="card">
        <div className="page-head">
          <h3>Cases</h3>
          <button type="button" className="primary" onClick={() => navigate(`/my/cases/new/${appliance.customerId}/${appliance.applianceId}`)}>
            + New case
          </button>
        </div>
        {cases.length === 0 ? (
          <p className="muted">No case for this appliance yet.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Case</th>
                  <th>Opened</th>
                  <th>Problem</th>
                  <th>State</th>
                  <th>Sync</th>
                </tr>
              </thead>
              <tbody>
                {cases.map((item) => (
                  <tr key={item.diagnosticCaseId}>
                    <td data-label="Case">
                      <a href={`#/my/cases/${item.diagnosticCaseId}`}>
                        <strong>{item.caseNo || 'Number pending'}</strong>
                      </a>
                    </td>
                    <td data-label="Opened">{new Date(item.openedAt).toLocaleDateString()}</td>
                    <td data-label="Problem">{item.primarySymptom ? symptomLabel(item.primarySymptom) : item.reportedSymptoms}</td>
                    <td data-label="State">
                      <StatusBadge status={item.workflowStatus} />
                    </td>
                    <td data-label="Sync">
                      <SyncBadge state={item.syncState} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {editing && (
        <ApplianceFormModal
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
