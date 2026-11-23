import { apiRequest } from '../../api/http'
import type {
  ApplianceDocumentPayload,
  ApplianceDocumentType,
  ApplianceModelPayload,
  AttachmentDownloadLink,
} from '../../api/types'
import { db } from '../../db/db'
import type { LocalApplianceDocument, LocalModel } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'
import { sha256Hex } from '../attachments/attachmentStore'
import { modelPayloadOf } from './modelStore'

/**
 * The schematic and the service manual of an appliance model.
 * The facts about a document travel through the outbox (REGISTER); the bytes are uploaded afterwards by the upload queue.
 * A model points at its documents, so linking a document is a change of the model (UPDATE) that waits for the REGISTER.
 */

/** The two kinds a model has a place for. */
export type DocumentKind = 'SCHEMATIC' | 'SERVICE_MANUAL'

// A guess at the limits of the server. The server checks them again.
const MAX_DOCUMENT_BYTES = 25 * 1024 * 1024

const MIME_TYPE_BY_EXTENSION: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
}

/** For the `accept` attribute of the file input. */
export const ACCEPTED_DOCUMENT_TYPES = 'application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png'

/** A document of the list, with what is known about its file on this device. */
export interface DocumentRow extends LocalApplianceDocument {
  /** The bytes are still on this device (waiting for the upload). */
  hasLocalFile: boolean
  /** Why the last upload attempt failed. */
  uploadError?: string
}

/** What the user chose in the dialog: an existing document, or a new one with its reference number. A file is optional. */
export interface DocumentChoice {
  existingId?: string
  documentRef?: string
  file?: File
}

interface PreparedFile {
  blob: Blob
  fileName: string
  sha256Hex: string
}

function mimeTypeOf(file: File): string {
  if (file.type) {
    return file.type
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return MIME_TYPE_BY_EXTENSION[extension] ?? ''
}

/** Checks the file and calculates its fingerprint. Done before any transaction: a transaction must not wait for anything that is not a database call. */
async function prepareFile(file: File): Promise<PreparedFile> {
  const mimeType = mimeTypeOf(file)
  if (!Object.values(MIME_TYPE_BY_EXTENSION).includes(mimeType)) {
    throw new Error(`"${file.name}" cannot be added. Only PDF, JPEG and PNG files are accepted.`)
  }
  if (file.size === 0) {
    throw new Error(`"${file.name}" is empty.`)
  }
  if (file.size > MAX_DOCUMENT_BYTES) {
    throw new Error(`"${file.name}" is larger than ${MAX_DOCUMENT_BYTES / 1024 / 1024} MB.`)
  }
  // The upload has to send the type that was announced, so the stored bytes carry it too.
  const blob = file.type === mimeType ? file : new File([file], file.name, { type: mimeType })
  return { blob, fileName: file.name.trim() || 'document', sha256Hex: await sha256Hex(blob) }
}

function fieldOf(kind: DocumentKind): 'schematicDocumentId' | 'serviceManualDocumentId' {
  return kind === 'SCHEMATIC' ? 'schematicDocumentId' : 'serviceManualDocumentId'
}

/** All documents of one kind, ordered by reference number. */
export async function listDocumentsOfType(documentType: ApplianceDocumentType): Promise<LocalApplianceDocument[]> {
  const documents = await db().applianceDocuments.where('documentType').equals(documentType).toArray()
  return documents.sort((a, b) => a.documentRef.localeCompare(b.documentRef))
}

export async function getDocumentRow(applianceDocumentId: string | null): Promise<DocumentRow | undefined> {
  if (!applianceDocumentId) {
    return undefined
  }
  const record = await db().applianceDocuments.get(applianceDocumentId)
  if (!record) {
    return undefined
  }
  const stored = await db().applianceDocumentFiles.get(applianceDocumentId)
  return { ...record, hasLocalFile: stored !== undefined, uploadError: stored?.lastError }
}

/**
 * Makes a document the schematic or the service manual of a model. The document and its REGISTER change are saved together
 * with the change of the model; the upload of the file starts at the next sync.
 */
export async function linkDocument(model: LocalModel, kind: DocumentKind, choice: DocumentChoice): Promise<void> {
  const prepared = choice.file ? await prepareFile(choice.file) : undefined
  const database = db()
  await database.transaction(
    'rw',
    [database.models, database.outbox, database.applianceDocuments, database.applianceDocumentFiles],
    async () => {
      let applianceDocumentId: string
      if (choice.existingId) {
        applianceDocumentId = choice.existingId
        const existing = await database.applianceDocuments.get(applianceDocumentId)
        if (!existing) {
          throw new Error('This document was not found on this device.')
        }
        if (prepared) {
          await storeFile(applianceDocumentId, existing, prepared)
        }
      } else {
        const documentRef = (choice.documentRef ?? '').trim()
        if (documentRef === '') {
          throw new Error('Enter the reference number printed on the document.')
        }
        const sameRef = await database.applianceDocuments
          .where('documentType')
          .equals(kind)
          .filter((other) => other.documentRef.toLowerCase() === documentRef.toLowerCase())
          .first()
        if (sameRef) {
          throw new Error(`A document with the reference "${sameRef.documentRef}" exists already. Choose it from the list.`)
        }
        applianceDocumentId = crypto.randomUUID()
        const payload: ApplianceDocumentPayload = { documentType: kind, documentRef }
        await database.applianceDocuments.add({
          applianceDocumentId,
          documentType: kind,
          documentRef,
          uploadStatus: 'NO_FILE',
          fileName: null,
          mimeType: null,
          byteSize: null,
          syncState: 'PENDING',
          createdAt: new Date().toISOString(),
        })
        await enqueueMutation({ entityType: 'APPLIANCE_DOCUMENT', entityId: applianceDocumentId, operation: 'REGISTER', payload })
        if (prepared) {
          await database.applianceDocumentFiles.put({ applianceDocumentId, file: prepared.blob, fileName: prepared.fileName, sha256Hex: prepared.sha256Hex })
        }
      }
      // The server refuses a model that points at a document it does not know, so a document that is still waiting to be registered goes first.
      const registerMutationId = await findCreateMutationId(applianceDocumentId)
      await changeModel(model.applianceModelId, { [fieldOf(kind)]: applianceDocumentId }, registerMutationId)
    },
  )
}

/**
 * Creates a model together with its schematic. The document is registered first and the model is created after it,
 * so the model's CREATE already points at the document and waits for its REGISTER (a later change of an unsent CREATE
 * would make the CREATE wait for something queued after it, which the server cannot process).
 * Returns the id of the new model.
 */
export async function createModelWithSchematic(form: ApplianceModelPayload, documentRef: string, file?: File): Promise<string> {
  const reference = documentRef.trim()
  if (reference === '') {
    throw new Error('Enter the reference number printed on the document.')
  }
  const prepared = file ? await prepareFile(file) : undefined
  const database = db()
  const applianceModelId = crypto.randomUUID()
  await database.transaction(
    'rw',
    [database.models, database.outbox, database.applianceDocuments, database.applianceDocumentFiles],
    async () => {
      const sameRef = await database.applianceDocuments
        .where('documentType')
        .equals('SCHEMATIC')
        .filter((other) => other.documentRef.toLowerCase() === reference.toLowerCase())
        .first()
      if (sameRef) {
        throw new Error(`A schematic with the reference "${sameRef.documentRef}" exists already. Add the model first, then choose it on the model page.`)
      }
      const applianceDocumentId = crypto.randomUUID()
      const documentPayload: ApplianceDocumentPayload = { documentType: 'SCHEMATIC', documentRef: reference }
      await database.applianceDocuments.add({
        applianceDocumentId,
        documentType: 'SCHEMATIC',
        documentRef: reference,
        uploadStatus: 'NO_FILE',
        fileName: null,
        mimeType: null,
        byteSize: null,
        syncState: 'PENDING',
        createdAt: new Date().toISOString(),
      })
      await enqueueMutation({ entityType: 'APPLIANCE_DOCUMENT', entityId: applianceDocumentId, operation: 'REGISTER', payload: documentPayload })
      if (prepared) {
        await database.applianceDocumentFiles.put({ applianceDocumentId, file: prepared.blob, fileName: prepared.fileName, sha256Hex: prepared.sha256Hex })
      }
      const registerMutationId = await findCreateMutationId(applianceDocumentId)
      const payload: ApplianceModelPayload = { ...form, schematicDocumentId: applianceDocumentId }
      await database.models.add({ applianceModelId, ...payload, syncState: 'PENDING' })
      await enqueueMutation({
        entityType: 'APPLIANCE_MODEL',
        entityId: applianceModelId,
        operation: 'CREATE',
        payload,
        dependsOn: registerMutationId ? [registerMutationId] : [],
      })
    },
  )
  return applianceModelId
}

/** The model no longer points at the document. The document itself stays (other models may use it). */
export async function unlinkDocument(model: LocalModel, kind: DocumentKind): Promise<void> {
  const database = db()
  await database.transaction('rw', [database.models, database.outbox], async () => {
    await changeModel(model.applianceModelId, { [fieldOf(kind)]: null })
  })
}

/** Adds the file to a document that was registered without one. */
export async function addDocumentFile(applianceDocumentId: string, file: File): Promise<void> {
  const prepared = await prepareFile(file)
  const database = db()
  await database.transaction('rw', [database.applianceDocuments, database.applianceDocumentFiles], async () => {
    const existing = await database.applianceDocuments.get(applianceDocumentId)
    if (!existing) {
      throw new Error('This document was not found on this device.')
    }
    await storeFile(applianceDocumentId, existing, prepared)
  })
}

/** Keeps the bytes for the upload. Call inside a transaction on applianceDocumentFiles. */
async function storeFile(applianceDocumentId: string, document: LocalApplianceDocument, prepared: PreparedFile): Promise<void> {
  if (document.uploadStatus === 'UPLOADED') {
    throw new Error(`The document "${document.documentRef}" has a file already.`)
  }
  if ((await db().applianceDocumentFiles.get(applianceDocumentId)) !== undefined) {
    throw new Error(`A file for "${document.documentRef}" is waiting to be uploaded already.`)
  }
  await db().applianceDocumentFiles.put({ applianceDocumentId, file: prepared.blob, fileName: prepared.fileName, sha256Hex: prepared.sha256Hex })
}

/** Saves a change of the documents of a model and queues it. Call inside a transaction on models and outbox. */
async function changeModel(
  applianceModelId: string,
  changes: Partial<Pick<ApplianceModelPayload, 'schematicDocumentId' | 'serviceManualDocumentId'>>,
  waitsFor?: string,
): Promise<void> {
  const database = db()
  // Read again inside the transaction: the model may have changed since the page was drawn.
  const fresh = await database.models.get(applianceModelId)
  if (!fresh) {
    throw new Error('This model was not found on this device.')
  }
  const payload: ApplianceModelPayload = { ...modelPayloadOf(fresh), ...changes }
  await database.models.update(applianceModelId, { ...changes, syncState: 'PENDING' })
  await enqueueMutation({
    entityType: 'APPLIANCE_MODEL',
    entityId: applianceModelId,
    operation: 'UPDATE',
    rowVersion: fresh.rowVersion,
    payload,
    dependsOn: waitsFor ? [waitsFor] : [],
  })
}

/** Opens a document in a new tab: the copy on this device if there is one, otherwise a short-lived link from the server. */
export async function openDocument(row: DocumentRow): Promise<void> {
  const stored = await db().applianceDocumentFiles.get(row.applianceDocumentId)
  if (stored) {
    const address = URL.createObjectURL(stored.file)
    window.open(address, '_blank')
    window.setTimeout(() => URL.revokeObjectURL(address), 60_000)
    return
  }
  const link = await apiRequest<AttachmentDownloadLink>(`/api/v1/appliance-documents/${row.applianceDocumentId}/download-url`)
  window.open(link.url, '_blank')
}
