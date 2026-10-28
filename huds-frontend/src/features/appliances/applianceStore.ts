import type { AppliancePayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalAppliance } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'

/** Same pattern as the customer store: the local record and the outbox change are saved in one transaction. */

export type ApplianceForm = Omit<AppliancePayload, 'applianceModelId'>

export async function listAppliances(): Promise<LocalAppliance[]> {
  const all = await db().appliances.toArray()
  return all.filter((appliance) => !appliance.archiving)
}

export async function listAppliancesOfModel(applianceModelId: string): Promise<LocalAppliance[]> {
  const all = await db().appliances.where('applianceModelId').equals(applianceModelId).toArray()
  return all.filter((appliance) => !appliance.archiving).sort((a, b) => a.serialNo.localeCompare(b.serialNo))
}

export function getAppliance(applianceId: string): Promise<LocalAppliance | undefined> {
  return db().appliances.get(applianceId)
}

export async function createAppliance(applianceModelId: string, form: ApplianceForm): Promise<void> {
  const database = db()
  const applianceId = crypto.randomUUID()
  const payload: AppliancePayload = { applianceModelId, ...form }
  await database.transaction('rw', database.appliances, database.outbox, async () => {
    await database.appliances.add({ applianceId, ...payload, syncState: 'PENDING' })
    // If the model itself is not on the server yet, the appliance has to wait for it.
    const modelCreate = await findCreateMutationId(applianceModelId)
    await enqueueMutation({
      entityType: 'APPLIANCE',
      entityId: applianceId,
      operation: 'CREATE',
      payload,
      dependsOn: modelCreate ? [modelCreate] : [],
    })
  })
}

export async function updateAppliance(appliance: LocalAppliance, form: ApplianceForm): Promise<void> {
  const database = db()
  const payload: AppliancePayload = { applianceModelId: appliance.applianceModelId, ...form }
  await database.transaction('rw', database.appliances, database.outbox, async () => {
    await database.appliances.update(appliance.applianceId, { ...form, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'APPLIANCE',
      entityId: appliance.applianceId,
      operation: 'UPDATE',
      rowVersion: appliance.rowVersion,
      payload,
    })
  })
}

export async function archiveAppliance(appliance: LocalAppliance): Promise<void> {
  const database = db()
  await database.transaction('rw', database.appliances, database.outbox, async () => {
    const result = await enqueueMutation({
      entityType: 'APPLIANCE',
      entityId: appliance.applianceId,
      operation: 'ARCHIVE',
      rowVersion: appliance.rowVersion,
    })
    if (result === 'DISCARDED') {
      await database.appliances.delete(appliance.applianceId)
    } else {
      await database.appliances.update(appliance.applianceId, { syncState: 'PENDING', archiving: true })
    }
  })
}
