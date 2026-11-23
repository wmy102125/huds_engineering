import type { CustomerDecision, DiagnosticOutcome, FailureCategory, RepairStatus } from '../../api/types'

export const DIAGNOSTIC_OUTCOME_LABELS: Record<DiagnosticOutcome, string> = {
  FAULT_CONFIRMED: 'Fault confirmed',
  NO_TROUBLE_FOUND: 'No trouble found',
  BEYOND_ECONOMICAL_REPAIR: 'Beyond economical repair',
}

export const REPAIR_STATUS_LABELS: Record<RepairStatus, string> = {
  REPAIRED: 'Repaired',
  PENDING_PARTS: 'Waiting for parts',
  INCOMPLETE: 'Incomplete',
}

export const CUSTOMER_DECISION_LABELS: Record<CustomerDecision, string> = {
  APPROVED: 'Approved',
  DECLINED: 'Declined',
  PENDING: 'Pending',
}

/** The subsystems a failure can belong to. */
export const FAILURE_CATEGORY_LABELS: Record<FailureCategory, string> = {
  PWR: 'Power supply',
  CTRL: 'Controls',
  SENS: 'Sensors',
  MTR: 'Motor',
  PMP: 'Pump',
  HEAT: 'Heating',
  REFRIG: 'Refrigeration',
  WTR_DRN: 'Water / drain',
  MECH: 'Mechanical',
  WIRE: 'Wiring',
}

export const FAILURE_CATEGORIES = Object.keys(FAILURE_CATEGORY_LABELS) as FailureCategory[]
