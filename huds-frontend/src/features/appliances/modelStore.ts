import type { ApplianceModelPayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalModel } from '../../db/db'
import { enqueueMutation } from '../../sync/outbox'

/** Same pattern as the customer store: the local record and the outbox change are saved in one transaction. */

export async function listModels(): Promise<LocalModel[]> {
  const all = await db().models.toArray()
  return all
    .filter((model) => !model.archiving)
    .sort((a, b) => `${a.applianceType} ${a.brand} ${a.modelNo}`.localeCompare(`${b.applianceType} ${b.brand} ${b.modelNo}`))
}

export function getModel(applianceModelId: string): Promise<LocalModel | undefined> {
  return db().models.get(applianceModelId)
}

export async function createModel(form: ApplianceModelPayload): Promise<void> {
  const database = db()
  const applianceModelId = crypto.randomUUID()
  await database.transaction('rw', database.models, database.outbox, async () => {
    await database.models.add({ applianceModelId, ...form, syncState: 'PENDING' })
    await enqueueMutation({ entityType: 'APPLIANCE_MODEL', entityId: applianceModelId, operation: 'CREATE', payload: form })
  })
}

export async function updateModel(model: LocalModel, form: ApplianceModelPayload): Promise<void> {
  const database = db()
  await database.transaction('rw', database.models, database.outbox, async () => {
    await database.models.update(model.applianceModelId, { ...form, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'APPLIANCE_MODEL',
      entityId: model.applianceModelId,
      operation: 'UPDATE',
      rowVersion: model.rowVersion,
      payload: form,
    })
  })
}

/** The server refuses it while appliances still use the model; the refusal shows up under "Needs attention". */
export async function archiveModel(model: LocalModel): Promise<void> {
  const database = db()
  await database.transaction('rw', database.models, database.outbox, async () => {
    const result = await enqueueMutation({
      entityType: 'APPLIANCE_MODEL',
      entityId: model.applianceModelId,
      operation: 'ARCHIVE',
      rowVersion: model.rowVersion,
    })
    if (result === 'DISCARDED') {
      await database.models.delete(model.applianceModelId)
    } else {
      await database.models.update(model.applianceModelId, { syncState: 'PENDING', archiving: true })
    }
  })
}
