import { useEffect, useRef, useState } from 'react'
import type { ChangeEventHandler, ClipboardEventHandler, DragEventHandler, TextareaHTMLAttributes } from 'react'
import { ACCEPTED_FILE_TYPES } from './attachmentStore'
import { nameClipboardFiles } from './pendingFilesState'

type TextareaProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onPaste' | 'onDrop' | 'onDragOver' | 'onDragLeave'>

/**
 * A text box that also takes photos and videos: pasted (a screenshot, a copied photo) or dragged into it.
 * A normal paste of text is left alone.
 */
export function FilesDropTextarea({ onFiles, ...rest }: TextareaProps & { onFiles: (files: File[]) => void }) {
  const [dragging, setDragging] = useState(false)

  const handlePaste: ClipboardEventHandler<HTMLTextAreaElement> = (event) => {
    const files = Array.from(event.clipboardData.files)
    if (files.length === 0 || event.clipboardData.getData('text/plain') !== '') {
      return
    }
    event.preventDefault()
    onFiles(nameClipboardFiles(files))
  }

  const handleDragOver: DragEventHandler<HTMLTextAreaElement> = (event) => {
    if (event.dataTransfer.types.includes('Files')) {
      event.preventDefault()
      setDragging(true)
    }
  }

  const handleDrop: DragEventHandler<HTMLTextAreaElement> = (event) => {
    setDragging(false)
    const files = Array.from(event.dataTransfer.files)
    if (files.length > 0) {
      event.preventDefault()
      onFiles(files)
    }
  }

  return (
    <textarea
      {...rest}
      onPaste={handlePaste}
      onDragOver={handleDragOver}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      style={dragging ? { outline: '2px dashed #2563eb' } : undefined}
    />
  )
}

/** One picked file: a photo is shown, a video can be played, with a button to take it out again. */
function Thumbnail({ file, onRemove }: { file: File; onRemove: () => void }) {
  const [url, setUrl] = useState<string | null>(null)
  // The browser cannot show every format (HEIC for example); then the name is shown instead.
  const [broken, setBroken] = useState(false)

  useEffect(() => {
    const address = URL.createObjectURL(file)
    // The address has to be created and released together with this effect, so it is set here.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setUrl(address)
    return () => URL.revokeObjectURL(address)
  }, [file])

  const isVideo = file.type.startsWith('video/')
  const frame = { width: 120, height: 90, objectFit: 'cover' as const, borderRadius: 6, background: '#eef1f5' }
  return (
    <div style={{ position: 'relative', width: 120 }}>
      {url && !broken && isVideo && <video src={url} style={frame} muted controls preload="metadata" onError={() => setBroken(true)} />}
      {url && !broken && !isVideo && <img src={url} alt={file.name} style={frame} onError={() => setBroken(true)} />}
      {(!url || broken) && <div style={{ ...frame, display: 'grid', placeItems: 'center' }} className="muted small">{isVideo ? 'Video' : 'Photo'}</div>}
      <button
        type="button"
        aria-label={`Remove ${file.name}`}
        onClick={onRemove}
        style={{ position: 'absolute', top: 2, right: 2, padding: '0 8px', minHeight: 0, lineHeight: '24px' }}
      >
        ×
      </button>
      <div className="muted small" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={file.name}>
        {file.name}
      </div>
    </div>
  )
}

interface ListProps {
  files: File[]
  onPick: (files: File[]) => void
  onRemove: (index: number) => void
  /** Why a file was refused, shown under the list. */
  problem?: string | null
}

/** The picked photos and videos as small pictures, and the button to pick more. Goes right under the text box. */
export function PendingFilesList({ files, onPick, onRemove, problem }: ListProps) {
  const input = useRef<HTMLInputElement>(null)

  const handleChosen: ChangeEventHandler<HTMLInputElement> = (event) => {
    const chosen = Array.from(event.target.files ?? [])
    // Reset, so choosing the same file again is noticed.
    event.target.value = ''
    onPick(chosen)
  }

  return (
    <div className="stack">
      {files.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {files.map((file, index) => (
            <Thumbnail key={`${file.name}-${file.size}-${index}`} file={file} onRemove={() => onRemove(index)} />
          ))}
        </div>
      )}
      <div className="row">
        <button type="button" onClick={() => input.current?.click()}>
          + Add photo or video
        </button>
        <span className="muted small">You can also paste or drag photos and videos into the box above.</span>
      </div>
      <input ref={input} type="file" accept={ACCEPTED_FILE_TYPES} multiple hidden onChange={handleChosen} />
      {problem && <p className="error">{problem}</p>}
    </div>
  )
}
