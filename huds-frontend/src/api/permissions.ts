import type { CurrentUser } from './types'

/** The authorities the screens look at. The server checks them again; here they only decide which buttons are shown. */
export const PERMISSION = {
  /** Plan, assign, reschedule and cancel visits (administrators). */
  VISIT_ASSIGN: 'VISIT_ASSIGN',
  /** Start, pause, resume and complete visits. */
  VISIT_WRITE: 'VISIT_WRITE',
  /** Every step of a case (administrators). */
  CASE_WRITE: 'CASE_WRITE',
  /** Only the steps between "In repair" and "Verifying" (technicians). */
  CASE_TRANSITION_FIELD: 'CASE_TRANSITION_FIELD',
  /** Add photos and videos to a case, an appliance or a visit. */
  ATTACHMENT_WRITE: 'ATTACHMENT_WRITE',
} as const

export function hasPermission(user: CurrentUser, permission: string): boolean {
  return user.permissions.includes(permission)
}
