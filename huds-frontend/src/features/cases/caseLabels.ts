import type { CaseDisposition, CaseStatus, ErrorCodeStatus, FailureTiming, PrimarySymptom } from '../../api/types'

/** What the user reads for every state of a case. */
export const STATUS_LABELS: Record<CaseStatus, string> = {
  OPEN: 'Open',
  DIAGNOSING: 'Diagnosing',
  PENDING_AUTHORIZATION: 'Waiting for approval',
  PENDING_PARTS: 'Waiting for parts',
  IN_REPAIR: 'In repair',
  ON_HOLD: 'On hold',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
}

export const DISPOSITION_LABELS: Record<CaseDisposition, string> = {
  FIXED_VERIFIED: 'Fixed and verified',
  UNRESOLVED: 'Not resolved',
  CUSTOMER_DECLINED: 'Customer declined',
  BER: 'Beyond economical repair',
  UNSAFE: 'Unsafe to repair',
  NO_FAULT_FOUND: 'No fault found',
  REFERRED: 'Referred elsewhere',
  CANCELLED: 'Cancelled',
}

/** A closed or cancelled case cannot change any more. */
export function isFinished(status: CaseStatus): boolean {
  return status === 'CLOSED' || status === 'CANCELLED'
}

export function statusLabel(status: CaseStatus): string {
  return STATUS_LABELS[status] ?? status
}

export function dispositionLabel(disposition: CaseDisposition | null): string {
  return disposition ? (DISPOSITION_LABELS[disposition] ?? disposition) : '-'
}

export const PRIMARY_SYMPTOM_LABELS: Record<PrimarySymptom, string> = {
  NO_POWER: 'No power',
  NO_START: "Won't start",
  NO_HEAT_COOL: 'No heat / no cooling',
  LEAK_DRAIN: 'Leak / drain problem',
  NOISE_VIBRATION: 'Noise / vibration',
  STOPS_MID_CYCLE: 'Stops mid-cycle',
  INTERMITTENT: 'Intermittent fault',
}

export const PRIMARY_SYMPTOMS = Object.keys(PRIMARY_SYMPTOM_LABELS) as PrimarySymptom[]

export function symptomLabel(symptom: PrimarySymptom | null): string {
  return symptom ? (PRIMARY_SYMPTOM_LABELS[symptom] ?? symptom) : '-'
}

/** When in the cycle the fault shows. The server stores the value as it is. */
export const TIMING_PHASE_LABELS: Record<string, string> = {
  AT_START: 'Right at the start',
  AFTER_START: 'A while after the start',
  MID_CYCLE: 'In the middle of the cycle',
  END_OF_CYCLE: 'At the end of the cycle',
  ANY_TIME: 'At random times',
}

/** "A while after the start (10 min)" or a dash. */
export function timingText(timing: FailureTiming): string {
  if (!timing.phase) {
    return '-'
  }
  const text = TIMING_PHASE_LABELS[timing.phase] ?? timing.phase
  return timing.afterMinutes ? `${text} (${timing.afterMinutes} min)` : text
}

export const ERROR_CODE_STATUS_LABELS: Record<ErrorCodeStatus, string> = {
  ACTIVE: 'Shown now',
  STORED_HISTORY: 'History only',
}
