import type { CaseStatus } from '../../api/types'
import { isFinished, statusLabel } from './caseLabels'

/** The state of a case as a small coloured label. */
export function StatusBadge({ status }: { status: CaseStatus }) {
  const kind = status === 'ON_HOLD' ? 'hold' : isFinished(status) ? 'done' : 'wait'
  return <span className={`badge ${kind}`}>{statusLabel(status)}</span>
}
