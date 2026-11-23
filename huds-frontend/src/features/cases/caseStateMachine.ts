import { hasPermission, PERMISSION } from '../../api/permissions'
import type { CaseDisposition, CaseStatus, CurrentUser } from '../../api/types'
import { statusLabel } from './caseLabels'

/**
 * The steps an administrator takes on a case by hand, as the server applies them.
 * The server is the judge; this copy only decides which buttons the screen offers.
 *
 * The visits move the case on by themselves:
 * - starting a visit: Open -> Diagnosing, Waiting for parts -> In repair (when all parts have arrived)
 * - completing a visit: the final status of its completion record decides the next state of the case
 * What is left for the administrator is the answer of the customer to the quote, putting a case on hold,
 * cancelling and closing it.
 */

export interface TransitionOption {
  toState: CaseStatus
  /** Text of the button. */
  label: string
  /** The user has to say why. */
  needsReason: boolean
  /** Set when the step ends the case: the dispositions the user may choose from. */
  dispositions?: CaseDisposition[]
}

const ALL_CLOSING: CaseDisposition[] = ['FIXED_VERIFIED', 'UNRESOLVED', 'CUSTOMER_DECLINED', 'BER', 'UNSAFE', 'NO_FAULT_FOUND', 'REFERRED']
/** "Fixed and verified" is only possible after the repair has started. */
const EARLY_CLOSING: CaseDisposition[] = ALL_CLOSING.filter((disposition) => disposition !== 'FIXED_VERIFIED')

const forward = (toState: CaseStatus, label: string): TransitionOption => ({ toState, label, needsReason: false })
const hold: TransitionOption = { toState: 'ON_HOLD', label: 'Put on hold', needsReason: true }
const cancel: TransitionOption = { toState: 'CANCELLED', label: 'Cancel case', needsReason: true }
const close = (dispositions: CaseDisposition[], label = 'Close case'): TransitionOption => ({
  toState: 'CLOSED',
  label,
  needsReason: false,
  dispositions,
})

/**
 * The steps that are allowed from a state.
 * @param resumeTo for a case on hold: the state it was in before it was put on hold (unknown: null)
 */
export function transitionOptions(from: CaseStatus, resumeTo: CaseStatus | null): TransitionOption[] {
  switch (from) {
    case 'OPEN':
      return [hold, cancel, close(EARLY_CLOSING)]
    case 'DIAGNOSING':
      return [
        forward('PENDING_AUTHORIZATION', 'Wait for approval'),
        forward('PENDING_PARTS', 'Wait for parts'),
        hold,
        cancel,
        close(EARLY_CLOSING),
      ]
    case 'PENDING_AUTHORIZATION':
      return [
        forward('IN_REPAIR', 'Customer approved: start repair'),
        forward('PENDING_PARTS', 'Customer approved: wait for parts'),
        close(['CUSTOMER_DECLINED'], 'Customer declined'),
        hold,
        cancel,
        close(EARLY_CLOSING),
      ]
    case 'PENDING_PARTS':
      return [forward('IN_REPAIR', 'Parts arrived: start repair'), hold, cancel, close(EARLY_CLOSING)]
    case 'IN_REPAIR':
      return [hold, close(ALL_CLOSING)]
    case 'ON_HOLD': {
      const options: TransitionOption[] = []
      if (resumeTo) {
        options.push({ toState: resumeTo, label: `Resume (${statusLabel(resumeTo)})`, needsReason: false })
      }
      options.push(cancel, close(EARLY_CLOSING))
      return options
    }
    case 'CLOSED':
    case 'CANCELLED':
      return []
  }
}

/**
 * The steps this user may take now.
 * - Administrators (CASE_WRITE) may take every step, except while a visit is open (the server refuses them then).
 * - Technicians do not move the case by hand; their visits do it.
 */
export function optionsForUser(options: TransitionOption[], user: CurrentUser, visitOpen: boolean): TransitionOption[] {
  if (visitOpen || !hasPermission(user, PERMISSION.CASE_WRITE)) {
    return []
  }
  return options
}
