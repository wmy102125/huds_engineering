import type { AuditFields } from '../db/db'

function formatTime(iso: string | undefined): string {
  return iso ? new Date(iso).toLocaleString() : ''
}

/** "Created by ... · Updated by ..." for the detail pages. Shows nothing for a record the server has not seen yet. */
export function AuditLine({ record }: { record: AuditFields }) {
  if (!record.createdAt) {
    return null
  }
  return (
    <p className="muted small">
      Created {formatTime(record.createdAt)}
      {record.createdByName ? ` by ${record.createdByName}` : ''} · Updated {formatTime(record.updatedAt)}
      {record.updatedByName ? ` by ${record.updatedByName}` : ''}
    </p>
  )
}
