import { useCallback, useState } from 'react'
import { checkAttachmentFile } from './attachmentStore'

/** Splits the files a user chose, pasted or dropped into the ones that can be added and the reasons for the others. */
export function splitAcceptable(chosen: File[]): { accepted: File[]; problems: string[] } {
  const accepted: File[] = []
  const problems: string[] = []
  for (const file of chosen) {
    try {
      checkAttachmentFile(file)
      accepted.push(file)
    } catch (caught) {
      problems.push(caught instanceof Error ? caught.message : `"${file.name}" cannot be added.`)
    }
  }
  return { accepted, problems }
}

const EXTENSION_BY_TYPE: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/heic': 'heic',
  'video/mp4': 'mp4',
  'video/quicktime': 'mov',
}

/** A screenshot taken from the clipboard is always called "image.png". Give every pasted file a name of its own. */
export function nameClipboardFiles(files: File[]): File[] {
  const stamp = Date.now()
  return files.map((file, index) => {
    if (!/^image\.\w+$/i.test(file.name)) {
      return file
    }
    const extension = EXTENSION_BY_TYPE[file.type] ?? file.name.split('.').pop() ?? 'png'
    return new File([file], `pasted-${stamp}-${index + 1}.${extension}`, { type: file.type })
  })
}

/** The photos and videos a user has picked on a form that are saved together with the record. */
export function usePendingFiles() {
  const [files, setFiles] = useState<File[]>([])
  const [problem, setProblem] = useState<string | null>(null)

  const add = useCallback((chosen: File[]) => {
    const { accepted, problems } = splitAcceptable(chosen)
    setFiles((current) => [...current, ...accepted])
    setProblem(problems.length > 0 ? problems.join(' ') : null)
  }, [])

  const remove = useCallback((index: number) => {
    setFiles((current) => current.filter((_, at) => at !== index))
  }, [])

  return { files, problem, add, remove }
}
