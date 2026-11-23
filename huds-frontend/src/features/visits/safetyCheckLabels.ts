import type { SafetyCheckCode } from '../../api/types'

/** The eight checks of section 2 of the diagnostic form, in the order of the form. */
export const SAFETY_CHECK_LABELS: Record<SafetyCheckCode, string> = {
  ELECTRICAL_ISOLATION: 'Electrical isolation',
  LOTO: 'Lockout / tagout',
  POWER_BREAKER: 'Power breaker checked',
  CORD_PLUG: 'Cord and plug checked',
  WATER_DRAIN_VENT: 'Water, drain and vent checked',
  LEAK_MOISTURE: 'Leaks and moisture checked',
  BURN_ODOR: 'Burn marks and odour checked',
  MECHANICAL_ACCESS: 'Mechanical access is safe',
}

export const SAFETY_CHECK_CODES = Object.keys(SAFETY_CHECK_LABELS) as SafetyCheckCode[]

export const SUPPLY_TYPE_LABELS = {
  V120_1PH: '120 V single phase',
  V120_240_SPLIT: '120/240 V split phase',
} as const

export const GAS_LABELS = {
  NG: 'Natural gas',
  LP: 'Propane (LP)',
} as const
