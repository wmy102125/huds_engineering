import { useCallback, useEffect, useState } from 'react'
import type { ChangeEventHandler, SubmitEventHandler } from 'react'
import type { LocalApplianceDocument, LocalModel } from '../../db/db'
import { Modal } from '../my/Modal'
import { ACCEPTED_DOCUMENT_TYPES, linkDocument, listDocumentsOfType } from './documentStore'
import type { DocumentKind } from './documentStore'

interface Props {
  model: LocalModel
  kind: DocumentKind
  onClose: () => void
  onSaved: () => void
}

const KIND_LABEL: Record<DocumentKind, string> = {
  SCHEMATIC: 'schematic',
  SERVICE_MANUAL: 'service manual',
}

/** Chooses the schematic or the service manual of a model: a document that is known already, or a new one. */
export function ModelDocumentModal({ model, kind, onClose, onSaved }: Props) {
  const [documents, setDocuments] = useState<LocalApplianceDocument[]>([])
  // An empty choice means "a new document".
  const [existingId, setExistingId] = useState('')
  const [documentRef, setDocumentRef] = useState('')
  const [file, setFile] = useState<File | undefined>(undefined)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadDocuments = useCallback(async () => {
    setDocuments(await listDocumentsOfType(kind))
  }, [kind])
  useEffect(() => {
    const timer = window.setTimeout(() => void loadDocuments(), 0)
    return () => window.clearTimeout(timer)
  }, [loadDocuments])

  const chosen = documents.find((document) => document.applianceDocumentId === existingId)
  // A file can be given for a new document, and for a known document that has no file yet.
  const canGiveFile = existingId === '' || chosen?.uploadStatus === 'NO_FILE'

  const handleFile: ChangeEventHandler<HTMLInputElement> = (event) => {
    setFile(event.target.files?.[0])
  }

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await linkDocument(model, kind, {
        existingId: existingId || undefined,
        documentRef,
        file: canGiveFile ? file : undefined,
      })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The document could not be saved.')
      setBusy(false)
      return
    }
    onSaved()
  }

  return (
    <Modal title={`Choose the ${KIND_LABEL[kind]}`} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Document
          <select value={existingId} onChange={(event) => setExistingId(event.target.value)}>
            <option value="">New document...</option>
            {documents.map((document) => (
              <option key={document.applianceDocumentId} value={document.applianceDocumentId}>
                {document.documentRef}
                {document.uploadStatus === 'UPLOADED' ? '' : ' (no file yet)'}
              </option>
            ))}
          </select>
        </label>
        {existingId === '' && (
          <label>
            Reference number printed on the document
            <input value={documentRef} onChange={(event) => setDocumentRef(event.target.value)} maxLength={120} autoComplete="off" autoFocus />
          </label>
        )}
        {canGiveFile && (
          <label>
            File (optional, PDF, JPEG or PNG)
            <input type="file" accept={ACCEPTED_DOCUMENT_TYPES} onChange={handleFile} />
          </label>
        )}
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary" disabled={busy}>
            {busy ? 'Saving...' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
