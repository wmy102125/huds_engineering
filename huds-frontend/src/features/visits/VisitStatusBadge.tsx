import type { VisitStatus } from '../../api/types'
import { isVisitFinished, visitStatusLabel } from './visitLabels'

/** The state of a visit as a small coloured label. */
export function VisitStatusBadge({ status }: { status: VisitStatus }) {
  const kind = status === 'PAUSED' ? 'hold' : isVisitFinished(status) ? 'done' : status === 'IN_PROGRESS' ? 'ok' : 'wait'
  return <span className={`badge ${kind}`}>{visitStatusLabel(status)}</span>
}
