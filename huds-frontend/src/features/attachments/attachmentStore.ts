import { apiRequest } from '../../api/http'
import type { AttachmentDownloadLink, AttachmentEntityType, AttachmentMediaKind, AttachmentPayload } from '../../api/types'
import { db } from '../../db/db'
import type { LocalAttachment } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'

/**
 * Photos and videos of a case, an appliance, a visit or a diagnostic step.
 * The facts about a file travel through the outbox (REGISTER); the bytes are uploaded afterwards by the upload queue.
 */

// The same limits as the server. The server checks them again.
const MAX_PHOTO_BYTES = 15 * 1024 * 1024
const MAX_VIDEO_BYTES = 200 * 1024 * 1024

const KIND_BY_MIME_TYPE: Record<string, AttachmentMediaKind> = {
  'image/jpeg': 'PHOTO',
  'image/png': 'PHOTO',
  'image/heic': 'PHOTO',
  'video/mp4': 'VIDEO',
  'video/quicktime': 'VIDEO',
}

/** Some browsers send no type for HEIC photos or MOV videos, so the extension is used then. */
const MIME_TYPE_BY_EXTENSION: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  heic: 'image/heic',
  mp4: 'video/mp4',
  mov: 'video/quicktime',
}

/** For the `accept` attribute of the file input. */
export const ACCEPTED_FILE_TYPES = 'image/jpeg,image/png,image/heic,video/mp4,video/quicktime,.jpg,.jpeg,.png,.heic,.mp4,.mov'

/** A file of the list, with what is known about it on this device. */
export interface AttachmentRow extends LocalAttachment {
  /** The bytes are still on this device (waiting for the upload, or just taken). */
  hasLocalFile: boolean
  /** Why the last upload attempt failed. */
  uploadError?: string
}

function mimeTypeOf(file: File): string {
  if (file.type) {
    return file.type
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return MIME_TYPE_BY_EXTENSION[extension] ?? ''
}

export async function sha256Hex(blob: Blob): Promise<string> {
  if (!crypto.subtle) {
    // The browser only offers this on https pages and on localhost.
    throw new Error('Files can only be added on a secure page (https).')
  }
  const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer())
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/** The files of one record, oldest first. */
export async function listAttachments(entityType: AttachmentEntityType, entityId: string): Promise<AttachmentRow[]> {
  const database = db()
  const records = await database.attachments
    .where('entityId')
    .equals(entityId)
    .and((record) => record.entityType === entityType)
    .toArray()
  records.sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''))
  // A second query instead of a join: which of these files are still on this device?
  const stored = await database.attachmentFiles.bulkGet(records.map((record) => record.attachmentId))
  return records.map((record, index) => ({
    ...record,
    hasLocalFile: stored[index] !== undefined,
    uploadError: stored[index]?.lastError,
  }))
}

/** Throws when the file cannot be added (wrong type, empty, too large). Lets a page refuse a file when it is chosen, before anything is saved. */
export function checkAttachmentFile(file: File): void {
  const mediaKind = KIND_BY_MIME_TYPE[mimeTypeOf(file)]
  if (!mediaKind) {
    throw new Error(`"${file.name}" cannot be added. Only JPEG, PNG and HEIC photos and MP4 and MOV videos are accepted.`)
  }
  const maxBytes = mediaKind === 'PHOTO' ? MAX_PHOTO_BYTES : MAX_VIDEO_BYTES
  if (file.size === 0) {
    throw new Error(`"${file.name}" is empty.`)
  }
  if (file.size > maxBytes) {
    throw new Error(`"${file.name}" is larger than ${maxBytes / 1024 / 1024} MB.`)
  }
}

/**
 * Adds a file to a record. The record and its REGISTER change are saved together; the upload starts at the next sync.
 * If the record is itself still waiting to be created on the server, the REGISTER waits behind it.
 */
export async function addAttachment(entityType: AttachmentEntityType, entityId: string, file: File): Promise<void> {
  checkAttachmentFile(file)
  const mimeType = mimeTypeOf(file)
  const mediaKind = KIND_BY_MIME_TYPE[mimeType]
  const fileName = file.name.trim() || (mediaKind === 'PHOTO' ? 'photo' : 'video')
  // The upload has to send the type that was announced, so the stored bytes carry it too.
  const blob = file.type === mimeType ? file : new Blob([file], { type: mimeType })
  // Calculated before the transaction: a transaction must not wait for anything that is not a database call.
  const sha = await sha256Hex(blob)

  const attachmentId = crypto.randomUUID()
  const capturedAt = file.lastModified > 0 ? new Date(file.lastModified).toISOString() : null
  const payload: AttachmentPayload = {
    entityType,
    entityId,
    fileName,
    mimeType,
    byteSize: file.size,
    sha256Hex: sha,
    capturedAt,
  }
  const database = db()
  await database.transaction('rw', database.attachments, database.attachmentFiles, database.outbox, async () => {
    await database.attachments.add({
      attachmentId,
      entityType,
      entityId,
      mediaKind,
      uploadStatus: 'PENDING',
      fileName,
      mimeType,
      byteSize: file.size,
      capturedAt,
      syncState: 'PENDING',
      createdAt: new Date().toISOString(),
    })
    await database.attachmentFiles.add({ attachmentId, file: blob })
    // The server refuses a file for a record it does not know, so a record that is still waiting to be created goes first.
    const recordCreate = await findCreateMutationId(entityId)
    await enqueueMutation({
      entityType: 'ATTACHMENT',
      entityId: attachmentId,
      operation: 'REGISTER',
      payload,
      dependsOn: recordCreate ? [recordCreate] : [],
    })
  })
}

/** Opens a file in a new tab: the copy on this device if there is one, otherwise a short-lived link from the server. */
export async function openAttachment(row: AttachmentRow): Promise<void> {
  const stored = await db().attachmentFiles.get(row.attachmentId)
  if (stored) {
    const address = URL.createObjectURL(stored.file)
    window.open(address, '_blank')
    window.setTimeout(() => URL.revokeObjectURL(address), 60_000)
    return
  }
  const link = await apiRequest<AttachmentDownloadLink>(`/api/v1/attachments/${row.attachmentId}/download-url`)
  window.open(link.url, '_blank')
}

/**
 * A picture address for a thumbnail: the copy on this device if there is one, otherwise a short-lived link from the server
 * (only for a file that is already uploaded). Returns null when there is no way to show it now (offline, not uploaded).
 */
export async function attachmentPreviewUrl(attachmentId: string, uploaded: boolean): Promise<{ url: string; revoke: () => void } | null> {
  const stored = await db().attachmentFiles.get(attachmentId)
  if (stored) {
    const url = URL.createObjectURL(stored.file)
    return { url, revoke: () => URL.revokeObjectURL(url) }
  }
  if (!uploaded) {
    return null
  }
  try {
    const link = await apiRequest<AttachmentDownloadLink>(`/api/v1/attachments/${attachmentId}/download-url`)
    return { url: link.url, revoke: () => {} }
  } catch {
    return null
  }
}
