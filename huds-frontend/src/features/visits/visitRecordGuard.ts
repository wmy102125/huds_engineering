import { hasPermission, PERMISSION } from '../../api/permissions'
import type { CurrentUser } from '../../api/types'
import type { LocalVisit } from '../../db/db'

/**
 * The records written during a visit (diagnostic steps, completion record) can only be written while the
 * technician is on site, by the technician of the visit or an administrator. The server checks the same rules.
 *
 * @returns why nothing can be written now, or null when it can
 */
export function visitRecordsBlockedReason(visit: LocalVisit, user: CurrentUser): string | null {
  if (visit.visitStatus === 'PLANNED') {
    return 'Start the visit first.'
  }
  if (visit.visitStatus !== 'IN_PROGRESS' && visit.visitStatus !== 'PAUSED') {
    return 'The visit is finished, so this can no longer be changed.'
  }
  if (visit.technicianId !== user.userId && !hasPermission(user, PERMISSION.VISIT_ASSIGN)) {
    return 'This visit is assigned to another technician.'
  }
  return null
}
