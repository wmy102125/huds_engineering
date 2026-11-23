import { useEffect, useRef, useState } from 'react'
import type { ChangeEventHandler } from 'react'
import { PERMISSION, hasPermission } from '../../api/permissions'
import type { AttachmentEntityType, CurrentUser } from '../../api/types'
import { ACCEPTED_FILE_TYPES, addAttachment, attachmentPreviewUrl, openAttachment } from './attachmentStore'
import type { AttachmentRow } from './attachmentStore'

interface Props {
  entityType: AttachmentEntityType
  entityId: string
  user: CurrentUser
  rows: AttachmentRow[]
  onChanged: () => void
  /** The record is finished (for example a closed or cancelled case): files can still be viewed but no file can be added. */
  readOnly?: boolean
}

function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** Says where a file is on its way: this device, the storage service, or done. */
function StatusBadge({ row }: { row: AttachmentRow }) {
  if (row.syncState === 'REJECTED') {
    return <span className="badge bad">Refused</span>
  }
  if (row.uploadStatus === 'UPLOADED' || row.uploadStatus === 'VERIFIED') {
    return <span className="badge ok">Uploaded</span>
  }
  if (row.syncState === 'PENDING') {
    return <span className="badge wait">Saved on this device</span>
  }
  return <span className="badge wait">{row.hasLocalFile ? 'Waiting to upload' : 'File not uploaded yet'}</span>
}

/** A small picture of a photo. Nothing is shown for a video, or when the picture cannot be loaded (offline, a format the browser cannot show). */
function Thumbnail({ attachmentId, uploaded }: { attachmentId: string; uploaded: boolean }) {
  const [url, setUrl] = useState<string | null>(null)
  const [broken, setBroken] = useState(false)

  useEffect(() => {
    let current = true
    let revoke = () => {}
    void attachmentPreviewUrl(attachmentId, uploaded).then((preview) => {
      if (!preview) {
        return
      }
      if (!current) {
        preview.revoke()
        return
      }
      revoke = preview.revoke
      setUrl(preview.url)
    })
    return () => {
      current = false
      revoke()
    }
  }, [attachmentId, uploaded])

  if (!url || broken) {
    return null
  }
  return (
    <img
      src={url}
      alt=""
      onError={() => setBroken(true)}
      style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 6, marginRight: 10, verticalAlign: 'middle' }}
    />
  )
}

/** The photos and videos of one record, with adding. The file stays on this device until it is uploaded. */
export function AttachmentsCard({ entityType, entityId, user, rows, onChanged, readOnly = false }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const editable = hasPermission(user, PERMISSION.ATTACHMENT_WRITE) && !readOnly

  const handleChosen: ChangeEventHandler<HTMLInputElement> = async (event) => {
    const files = Array.from(event.target.files ?? [])
    // Reset, so choosing the same file again is noticed.
    event.target.value = ''
    if (files.length === 0) {
      return
    }
    setBusy(true)
    setError(null)
    const problems: string[] = []
    for (const file of files) {
      try {
        await addAttachment(entityType, entityId, file)
      } catch (caught) {
        problems.push(caught instanceof Error ? caught.message : `"${file.name}" could not be added.`)
      }
    }
    setBusy(false)
    if (problems.length > 0) {
      setError(problems.join(' '))
    }
    onChanged()
  }

  const handleOpen = async (row: AttachmentRow) => {
    setError(null)
    try {
      await openAttachment(row)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The file could not be opened.')
    }
  }

  return (
    <div className="card">
      <div className="page-head">
        <h3>Photos and videos</h3>
        {editable && (
          <>
            <button type="button" disabled={busy} onClick={() => input.current?.click()}>
              {busy ? 'Adding...' : '+ Add photo or video'}
            </button>
            <input ref={input} type="file" accept={ACCEPTED_FILE_TYPES} multiple hidden onChange={handleChosen} />
          </>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      {rows.length === 0 ? (
        <p className="muted">No photo or video yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>File</th>
                <th>Size</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const uploaded = row.uploadStatus === 'UPLOADED' || row.uploadStatus === 'VERIFIED'
                return (
                  <tr key={row.attachmentId}>
                    <td data-label="File">
                      {row.mediaKind !== 'VIDEO' && <Thumbnail attachmentId={row.attachmentId} uploaded={uploaded} />}
                      <strong>{row.fileName}</strong>
                      <span className="muted"> · {row.mediaKind === 'VIDEO' ? 'Video' : 'Photo'}</span>
                      {row.uploadError && <p className="error small">{row.uploadError}</p>}
                    </td>
                    <td data-label="Size">{formatBytes(row.byteSize)}</td>
                    <td data-label="Status">
                      <StatusBadge row={row} />
                    </td>
                    <td>
                      <button type="button" disabled={!row.hasLocalFile && !uploaded} onClick={() => void handleOpen(row)}>
                        View
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
