import { useRef, useState } from 'react'
import type { ChangeEventHandler } from 'react'
import type { LocalModel } from '../../db/db'
import { ACCEPTED_DOCUMENT_TYPES, addDocumentFile, openDocument, unlinkDocument } from './documentStore'
import type { DocumentKind, DocumentRow } from './documentStore'
import { ModelDocumentModal } from './ModelDocumentModal'

interface Props {
  model: LocalModel
  schematic: DocumentRow | undefined
  onChanged: () => void
}

/** Says where the file of a document is on its way: this device, the storage service, or done. */
function StatusBadge({ row }: { row: DocumentRow }) {
  if (row.syncState === 'REJECTED') {
    return <span className="badge bad">Refused</span>
  }
  if (row.uploadStatus === 'UPLOADED') {
    return <span className="badge ok">Uploaded</span>
  }
  if (row.syncState === 'PENDING') {
    return <span className="badge wait">Saved on this device</span>
  }
  if (row.hasLocalFile) {
    return <span className="badge wait">Waiting to upload</span>
  }
  return <span className="badge wait">No file yet</span>
}

interface DocumentLineProps {
  label: string
  kind: DocumentKind
  row: DocumentRow | undefined
  onChoose: (kind: DocumentKind) => void
  onRemove: (kind: DocumentKind) => void
  onFileChosen: (row: DocumentRow, file: File) => void
  onOpen: (row: DocumentRow) => void
}

function DocumentLine({ label, kind, row, onChoose, onRemove, onFileChosen, onOpen }: DocumentLineProps) {
  const input = useRef<HTMLInputElement>(null)
  const handleChosen: ChangeEventHandler<HTMLInputElement> = (event) => {
    const file = event.target.files?.[0]
    // Reset, so choosing the same file again is noticed.
    event.target.value = ''
    if (row && file) {
      onFileChosen(row, file)
    }
  }
  const canOpen = row !== undefined && (row.hasLocalFile || row.uploadStatus === 'UPLOADED')
  const needsFile = row !== undefined && row.uploadStatus !== 'UPLOADED' && !row.hasLocalFile

  return (
    <tr>
      <td data-label="Document">
        <strong>{label}</strong>
      </td>
      <td data-label="Reference">
        {row ? row.documentRef : <span className="muted">-</span>}
        {row?.uploadError && <p className="error small">{row.uploadError}</p>}
      </td>
      <td data-label="Status">{row ? <StatusBadge row={row} /> : null}</td>
      <td>
        <span className="row">
          {row && (
            <button type="button" disabled={!canOpen} onClick={() => onOpen(row)}>
              View
            </button>
          )}
          {needsFile && (
            <>
              <button type="button" onClick={() => input.current?.click()}>
                Add file
              </button>
              <input ref={input} type="file" accept={ACCEPTED_DOCUMENT_TYPES} hidden onChange={handleChosen} />
            </>
          )}
          <button type="button" onClick={() => onChoose(kind)}>
            {row ? 'Change' : 'Choose'}
          </button>
          {row && (
            <button type="button" onClick={() => onRemove(kind)}>
              Remove
            </button>
          )}
        </span>
      </td>
    </tr>
  )
}

/** The schematic of a model. Every appliance of the model uses the same one. (The service manual is not offered for now.) */
export function ModelDocumentsCard({ model, schematic, onChanged }: Props) {
  const [choosing, setChoosing] = useState<DocumentKind | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async (action: () => Promise<void>) => {
    setError(null)
    try {
      await action()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'This could not be done.')
      return
    }
    onChanged()
  }

  const lineProps = {
    onChoose: (kind: DocumentKind) => setChoosing(kind),
    onRemove: (kind: DocumentKind) => void run(() => unlinkDocument(model, kind)),
    onFileChosen: (row: DocumentRow, file: File) => void run(() => addDocumentFile(row.applianceDocumentId, file)),
    onOpen: (row: DocumentRow) => void run(() => openDocument(row)),
  }

  return (
    <div className="card">
      <h3>Schematic</h3>
      <p className="muted">The schematic belongs to the model, so every appliance of this model uses it.</p>
      {error && <p className="error">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Document</th>
              <th>Reference</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <DocumentLine label="Schematic" kind="SCHEMATIC" row={schematic} {...lineProps} />
          </tbody>
        </table>
      </div>
      {choosing && (
        <ModelDocumentModal
          model={model}
          kind={choosing}
          onClose={() => setChoosing(null)}
          onSaved={() => {
            setChoosing(null)
            onChanged()
          }}
        />
      )}
    </div>
  )
}
