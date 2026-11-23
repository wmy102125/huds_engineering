import { apiRequest, ApiError } from '../api/http'
import type { ApplianceDocumentChange, ApplianceDocumentUploadPayload, ApplianceDocumentUploadResult } from '../api/types'
import { db } from '../db/db'

let uploading = false

/**
 * Sends the files of schematics and service manuals that wait on this device to the storage service, one after the other.
 *
 * A file is sent only after the server has accepted the REGISTER of its document (the record is then SYNCED), because the
 * upload link can only be asked for a document the server knows. Every step can be repeated safely, so a run that
 * is interrupted (tab closed, connection lost) is simply started again by the next sync:
 *   1. ask for an upload link,  2. send the bytes to the link,  3. tell the server the upload is complete.
 * The bytes go straight to the storage service, not through the backend and not through the outbox.
 *
 * @param onChange called after every file, so the page can show the new state while the next file is still on its way
 */
export async function uploadWaitingDocuments(onChange: () => void): Promise<void> {
  if (uploading) {
    return
  }
  uploading = true
  try {
    const database = db()
    const ids = await database.applianceDocumentFiles.toCollection().primaryKeys()
    for (const applianceDocumentId of ids) {
      const record = await database.applianceDocuments.get(applianceDocumentId)
      // The server does not know the document yet (or refused it): there is nothing to upload to.
      if (!record || record.syncState !== 'SYNCED') {
        continue
      }
      // Uploaded already, for example by an earlier run whose answer was lost: the bytes are no longer needed.
      if (record.uploadStatus === 'UPLOADED') {
        await database.applianceDocumentFiles.delete(applianceDocumentId)
        onChange()
        continue
      }
      const finished = await uploadOne(applianceDocumentId)
      onChange()
      if (!finished) {
        // The server cannot be reached: the other files would fail the same way.
        return
      }
    }
  } finally {
    uploading = false
  }
}

/** Returns false when the server could not be reached, true otherwise (also after a failure that is shown to the user). */
async function uploadOne(applianceDocumentId: string): Promise<boolean> {
  const database = db()
  const stored = await database.applianceDocumentFiles.get(applianceDocumentId)
  if (!stored) {
    return true
  }
  try {
    const request: ApplianceDocumentUploadPayload = {
      fileName: stored.fileName,
      mimeType: stored.file.type,
      byteSize: stored.file.size,
      sha256Hex: stored.sha256Hex,
    }
    const issued = await apiRequest<ApplianceDocumentUploadResult>(`/api/v1/appliance-documents/${applianceDocumentId}/upload-url`, {
      method: 'POST',
      body: request,
    })

    let response: Response
    try {
      response = await fetch(issued.upload.url, { method: 'PUT', headers: issued.upload.headers, body: stored.file })
    } catch {
      throw new Error('The storage service cannot be reached. Check the connection, or the CORS setting of the bucket.')
    }
    if (!response.ok) {
      throw new Error(`The storage service refused the file (HTTP ${response.status}).`)
    }

    const done = await apiRequest<ApplianceDocumentChange>(`/api/v1/appliance-documents/${applianceDocumentId}/complete-upload`, {
      method: 'POST',
    })
    await database.transaction('rw', database.applianceDocuments, database.applianceDocumentFiles, async () => {
      await database.applianceDocuments.update(applianceDocumentId, {
        uploadStatus: done.uploadStatus,
        fileName: done.fileName,
        mimeType: done.mimeType,
        byteSize: done.byteSize,
        rowVersion: done.rowVersion,
        updatedAt: done.updatedAt,
      })
      await database.applianceDocumentFiles.delete(applianceDocumentId)
    })
    return true
  } catch (error) {
    if (error instanceof ApiError && error.isNetworkError) {
      return false
    }
    const message = error instanceof Error ? error.message : 'The upload failed.'
    console.warn(`Upload of document ${applianceDocumentId} failed:`, error)
    await database.applianceDocumentFiles.update(applianceDocumentId, { lastError: message })
    return true
  }
}
