import { apiRequest, ApiError } from '../api/http'
import type { AttachmentChange, PresignedUpload } from '../api/types'
import { db } from '../db/db'

let uploading = false

/**
 * Sends the files that wait on this device to the storage service, one after the other.
 *
 * A file is sent only after the server has accepted its REGISTER (the record is then SYNCED), because the
 * upload link can only be asked for a file the server knows. Every step can be repeated safely, so a run that
 * is interrupted (tab closed, connection lost) is simply started again by the next sync:
 *   1. ask for an upload link,  2. send the bytes to the link,  3. tell the server the upload is complete.
 * The bytes go straight to the storage service, not through the backend and not through the outbox.
 *
 * @param onChange called after every file, so the page can show the new state while the next file is still on its way
 */
export async function uploadWaiting(onChange: () => void): Promise<void> {
  if (uploading) {
    return
  }
  uploading = true
  try {
    const database = db()
    const ids = await database.attachmentFiles.toCollection().primaryKeys()
    for (const attachmentId of ids) {
      const record = await database.attachments.get(attachmentId)
      // The server does not know the file yet (or refused it): there is nothing to upload to.
      if (!record || record.syncState !== 'SYNCED') {
        continue
      }
      // Uploaded already, for example by an earlier run whose answer was lost: the bytes are no longer needed.
      if (record.uploadStatus === 'UPLOADED' || record.uploadStatus === 'VERIFIED') {
        await database.attachmentFiles.delete(attachmentId)
        onChange()
        continue
      }
      const finished = await uploadOne(attachmentId)
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
async function uploadOne(attachmentId: string): Promise<boolean> {
  const database = db()
  const stored = await database.attachmentFiles.get(attachmentId)
  if (!stored) {
    return true
  }
  try {
    const link = await apiRequest<PresignedUpload>(`/api/v1/attachments/${attachmentId}/upload-url`, { method: 'POST' })

    let response: Response
    try {
      response = await fetch(link.url, { method: 'PUT', headers: link.headers, body: stored.file })
    } catch {
      throw new Error('The storage service cannot be reached. Check the connection, or the CORS setting of the bucket.')
    }
    if (!response.ok) {
      throw new Error(`The storage service refused the file (HTTP ${response.status}).`)
    }

    const done = await apiRequest<AttachmentChange>(`/api/v1/attachments/${attachmentId}/complete-upload`, { method: 'POST' })
    await database.transaction('rw', database.attachments, database.attachmentFiles, async () => {
      await database.attachments.update(attachmentId, {
        uploadStatus: done.uploadStatus,
        rowVersion: done.rowVersion,
        updatedAt: done.updatedAt,
      })
      await database.attachmentFiles.delete(attachmentId)
    })
    return true
  } catch (error) {
    if (error instanceof ApiError && error.isNetworkError) {
      return false
    }
    const message = error instanceof Error ? error.message : 'The upload failed.'
    console.warn(`Upload of ${attachmentId} failed:`, error)
    await database.attachmentFiles.update(attachmentId, { lastError: message })
    return true
  }
}
