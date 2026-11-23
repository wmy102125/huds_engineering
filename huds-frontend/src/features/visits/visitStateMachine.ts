import type { VisitStatus } from '../../api/types'

/**
 * The rules of the visit state machine, as the server applies them (see the visit spec, section 2).
 * The server is the judge; this copy only decides which buttons the screen offers.
 *
 *   PLANNED -> IN_PROGRESS (the technician starts the visit) -> COMPLETED
 *   The safety checks are filled in on the visit page while it is in progress; a visit cannot be completed without them.
 */

export interface VisitStep {
  toState: VisitStatus
  /** Text of the button. */
  label: string
  /** The user has to say why. */
  needsReason: boolean
  /** Only an administrator (VISIT_ASSIGN) may take this step. */
  adminOnly: boolean
}

const START: VisitStep = { toState: 'IN_PROGRESS', label: 'Start visit', needsReason: false, adminOnly: false }
const RESUME: VisitStep = { toState: 'IN_PROGRESS', label: 'Resume', needsReason: false, adminOnly: false }
const PAUSE: VisitStep = { toState: 'PAUSED', label: 'Pause', needsReason: true, adminOnly: false }
const COMPLETE: VisitStep = { toState: 'COMPLETED', label: 'Complete visit', needsReason: false, adminOnly: false }
// The server asks for a reason when a visit is cancelled.
const CANCEL: VisitStep = { toState: 'CANCELLED', label: 'Cancel visit', needsReason: true, adminOnly: true }

/** The steps that are allowed from a state. */
export function visitSteps(from: VisitStatus): VisitStep[] {
  switch (from) {
    case 'PLANNED':
      return [START, CANCEL]
    case 'IN_PROGRESS':
      return [PAUSE, COMPLETE]
    case 'PAUSED':
      return [RESUME, COMPLETE]
    case 'COMPLETED':
    case 'CANCELLED':
      return []
  }
}
