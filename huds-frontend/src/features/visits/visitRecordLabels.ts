import type {
  FinalStatus,
  FixOutcome,
  GoogleReviewStatus,
  PowerState,
  StepResult,
  VerificationCheckCode,
} from '../../api/types'

export const POWER_STATE_LABELS: Record<PowerState, string> = {
  STATIC: 'Static (power off)',
  LIVE: 'Live (power on)',
}

export const STEP_RESULT_LABELS: Record<StepResult, string> = {
  PASS: 'Pass',
  FAIL: 'Fail',
}

/** The checks of section 5 of the diagnostic form, in the order of the form. The front end decides the content. */
export const VERIFICATION_CHECK_LABELS: Record<VerificationCheckCode, string> = {
  CURRENT_AMPS_IN_SPEC: 'Current draw is within specification',
  RELEVANT_CYCLE_COMPLETED: 'A relevant cycle was completed',
  NO_LEAK_VERIFIED: 'No leak verified',
  GROUND_SAFETY_CHECKED: 'Ground and safety checked',
}

export const VERIFICATION_CHECK_CODES = Object.keys(VERIFICATION_CHECK_LABELS) as VerificationCheckCode[]

export const FINAL_STATUS_LABELS: Record<FinalStatus, string> = {
  OPERATING_NORMALLY: 'Operating normally',
  FURTHER_TESTING: 'Further testing needed',
  QUOTE_PENDING_APPROVAL: 'Quote pending approval',
  PARTS_REQUIRED: 'Parts required',
  NO_FAULT_FOUND: 'No fault found',
  REPAIR_DECLINED: 'Repair declined',
  NOT_REPAIRABLE: 'Not repairable',
}

export const FIX_OUTCOME_LABELS: Record<FixOutcome, string> = {
  FIRST_TIME_FIX: 'First-time fix',
  RETURN_VISIT_REQUIRED: 'Return visit required',
  RECALL_RESOLVED: 'Recall resolved',
}

export const GOOGLE_REVIEW_LABELS: Record<GoogleReviewStatus, string> = {
  NOT_REQUESTED: 'Not requested',
  PENDING: 'Pending',
  REQUESTED: 'Requested',
  COMPLETED: 'Completed',
  DECLINED: 'Declined',
}

/** "Label" for a value of a label table, or a dash for an empty value. */
export function labelOf<T extends string>(labels: Record<T, string>, value: T | null | undefined): string {
  return value ? (labels[value] ?? value) : '-'
}
