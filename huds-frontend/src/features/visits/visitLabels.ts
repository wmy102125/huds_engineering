import type { VisitKind, VisitStatus } from '../../api/types'

/** What the user reads for every state of a visit. */
export const VISIT_STATUS_LABELS: Record<VisitStatus, string> = {
  PLANNED: 'Planned',
  IN_PROGRESS: 'On site',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

export const VISIT_KIND_LABELS: Record<VisitKind, string> = {
  FIRST: 'First visit',
  FOLLOW_UP: 'Follow-up visit',
  RECALL: 'Recall (after an earlier repair)',
}

export function visitKindLabel(kind: VisitKind | undefined): string {
  return kind ? (VISIT_KIND_LABELS[kind] ?? kind) : '-'
}

export function visitStatusLabel(status: VisitStatus): string {
  return VISIT_STATUS_LABELS[status] ?? status
}

/** The technician is on site, or has paused the work on site. */
export function isVisitOpen(status: VisitStatus): boolean {
  return status === 'IN_PROGRESS' || status === 'PAUSED'
}

/** A completed or cancelled visit cannot change any more. */
export function isVisitFinished(status: VisitStatus): boolean {
  return status === 'COMPLETED' || status === 'CANCELLED'
}

/** "Jan 5, 2026, 9:30 AM" or a dash for a visit without a planned time. */
export function formatVisitTime(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '-'
}

/** The value of a datetime-local input for a stored time (the input works in local time and has no zone). */
export function toDateTimeInput(iso: string | null): string {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** The stored time (UTC text) for the value of a datetime-local input. Empty input means "no time yet". */
export function fromDateTimeInput(value: string): string | null {
  return value === '' ? null : new Date(value).toISOString()
}
