import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CaseDisposition, CaseStatus } from '../../api/types'
import type { LocalCase } from '../../db/db'
import { addAttachment } from '../attachments/attachmentStore'
import { FilesDropTextarea, PendingFilesList } from '../attachments/PendingFiles'
import { usePendingFiles } from '../attachments/pendingFilesState'
import { Modal } from '../my/Modal'
import { CaseClassificationFields, classificationFrom } from './CaseClassificationFields'
import { DISPOSITION_LABELS, statusLabel } from './caseLabels'
import { transitionCase, updateCaseText } from './caseStore'
import type { TransitionOption } from './caseStateMachine'

interface EditProps {
  caseRecord: LocalCase
  onClose: () => void
  onSaved: () => void
}

/** Change what the customer reported and how it is classified. Who, where and what appliance cannot be changed. */
export function CaseTextModal({ caseRecord, onClose, onSaved }: EditProps) {
  const [symptoms, setSymptoms] = useState(caseRecord.reportedSymptoms)
  const [classification, setClassification] = useState(
    classificationFrom(caseRecord.primarySymptom, caseRecord.secondarySymptoms, caseRecord.failureTiming),
  )
  const [error, setError] = useState<string | null>(null)
  // Photos and videos chosen while changing the problem. They are added to the case when the changes are saved.
  const pending = usePendingFiles()

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await updateCaseText(caseRecord, { reportedSymptoms: symptoms, classification })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The case could not be saved.')
      return
    }
    // The text is saved. A file that fails does not undo it.
    const failed: string[] = []
    for (const file of pending.files) {
      try {
        await addAttachment('CASE', caseRecord.diagnosticCaseId, file)
      } catch (caught) {
        failed.push(caught instanceof Error ? caught.message : `"${file.name}" could not be added.`)
      }
    }
    if (failed.length > 0) {
      window.alert(`The changes were saved, but some files were not added. You can add them again in "Photos and videos".\n${failed.join('\n')}`)
    }
    onSaved()
  }

  return (
    <Modal title="Edit case" onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Problem reported by the customer
          <FilesDropTextarea value={symptoms} onChange={(event) => setSymptoms(event.target.value)} rows={4} autoFocus onFiles={pending.add} />
        </label>
        <PendingFilesList files={pending.files} onPick={pending.add} onRemove={pending.remove} problem={pending.problem} />
        <CaseClassificationFields value={classification} onChange={setClassification} />
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            Save changes
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface TransitionProps {
  caseRecord: LocalCase
  resumeTo: CaseStatus | null
  option: TransitionOption
  onClose: () => void
  onSaved: () => void
}

/** Asks for what the step needs (a reason, or how the case ended) and records the step. */
export function TransitionModal({ caseRecord, resumeTo, option, onClose, onSaved }: TransitionProps) {
  const [reason, setReason] = useState('')
  const [disposition, setDisposition] = useState<CaseDisposition | ''>('')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await transitionCase(caseRecord, resumeTo, {
        toState: option.toState,
        reason,
        disposition: disposition === '' ? null : disposition,
      })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The step could not be saved.')
      return
    }
    onSaved()
  }

  const closing = option.dispositions !== undefined
  return (
    <Modal title={option.label} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <p className="muted">
          {statusLabel(caseRecord.workflowStatus)} → {statusLabel(option.toState)}
        </p>
        {closing && (
          <label>
            How did the case end?
            <select value={disposition} onChange={(event) => setDisposition(event.target.value as CaseDisposition | '')} autoFocus>
              <option value="">Choose...</option>
              {option.dispositions?.map((value) => (
                <option key={value} value={value}>
                  {DISPOSITION_LABELS[value]}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          {option.needsReason ? 'Reason' : 'Note (optional)'}
          <textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} autoFocus={!closing} />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            Confirm
          </button>
        </div>
      </form>
    </Modal>
  )
}
