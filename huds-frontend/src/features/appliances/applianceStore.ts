import type { AppliancePayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalAppliance } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'
import { countCasesOf } from '../cases/caseStore'

/** Same pattern as the customer store: the local record and the outbox change are saved in one transaction. */

export type ApplianceForm = Omit<AppliancePayload, 'applianceModelId' | 'customerId'>

export async function listAppliances(): Promise<LocalAppliance[]> {
  const all = await db().appliances.toArray()
  return all.filter((appliance) => !appliance.archiving)
}

export async function listAppliancesOfModel(applianceModelId: string): Promise<LocalAppliance[]> {
  const all = await db().appliances.where('applianceModelId').equals(applianceModelId).toArray()
  return all.filter((appliance) => !appliance.archiving).sort((a, b) => a.serialNo.localeCompare(b.serialNo))
}

export async function listAppliancesOfCustomer(customerId: string): Promise<LocalAppliance[]> {
  const all = await db().appliances.where('customerId').equals(customerId).toArray()
  return all.filter((appliance) => !appliance.archiving).sort((a, b) => a.serialNo.localeCompare(b.serialNo))
}

export function getAppliance(applianceId: string): Promise<LocalAppliance | undefined> {
  return db().appliances.get(applianceId)
}

/** Returns the id of the new appliance. */
export async function createAppliance(customerId: string, applianceModelId: string, form: ApplianceForm): Promise<string> {
  const database = db()
  const applianceId = crypto.randomUUID()
  const payload: AppliancePayload = { customerId, applianceModelId, ...form }
  await database.transaction('rw', database.appliances, database.outbox, async () => {
    await database.appliances.add({ applianceId, ...payload, syncState: 'PENDING' })
    // If the customer or the model is not on the server yet, the appliance has to wait for it.
    const dependsOn: string[] = []
    for (const parentId of [customerId, applianceModelId]) {
      const parentCreate = await findCreateMutationId(parentId)
      if (parentCreate) {
        dependsOn.push(parentCreate)
      }
    }
    await enqueueMutation({ entityType: 'APPLIANCE', entityId: applianceId, operation: 'CREATE', payload, dependsOn })
  })
  return applianceId
}

export async function updateAppliance(appliance: LocalAppliance, form: ApplianceForm): Promise<void> {
  const database = db()
  // The owner and the model never change after creation.
  const payload: AppliancePayload = { customerId: appliance.customerId, applianceModelId: appliance.applianceModelId, ...form }
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
  // The server refuses to archive an appliance that has cases, so do not even try.
  if ((await countCasesOf('applianceId', appliance.applianceId)) > 0) {
    throw new Error('This appliance has cases and cannot be archived.')
  }
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
