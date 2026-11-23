import type { FailureTiming, PrimarySymptom } from '../../api/types'
import { PRIMARY_SYMPTOM_LABELS, PRIMARY_SYMPTOMS, TIMING_PHASE_LABELS } from './caseLabels'

/** The classification of the problem as the form edits it. afterMinutes is text while the user types. */
export interface ClassificationForm {
  primarySymptom: PrimarySymptom | ''
  secondarySymptoms: PrimarySymptom[]
  phase: string
  afterMinutes: string
}

export const EMPTY_CLASSIFICATION: ClassificationForm = { primarySymptom: '', secondarySymptoms: [], phase: '', afterMinutes: '' }

export function classificationFrom(
  primarySymptom: PrimarySymptom | null,
  secondarySymptoms: PrimarySymptom[],
  timing: FailureTiming,
): ClassificationForm {
  return {
    primarySymptom: primarySymptom ?? '',
    secondarySymptoms,
    phase: timing.phase ?? '',
    afterMinutes: timing.afterMinutes ? String(timing.afterMinutes) : '',
  }
}

/** The failure timing to store; no phase means no timing. Throws a readable message for a bad number of minutes. */
export function timingFrom(form: ClassificationForm): FailureTiming {
  if (form.phase === '') {
    return {}
  }
  const text = form.afterMinutes.trim()
  if (text === '') {
    return { phase: form.phase }
  }
  const minutes = Number(text)
  if (!Number.isInteger(minutes) || minutes < 0 || minutes > 10000) {
    throw new Error('Minutes must be a whole number.')
  }
  return { phase: form.phase, afterMinutes: minutes }
}

interface Props {
  value: ClassificationForm
  onChange: (value: ClassificationForm) => void
}

/** Main symptom, other symptoms and when the fault shows. Used when a case is opened and when it is edited. */
export function CaseClassificationFields({ value, onChange }: Props) {
  const toggle = (symptom: PrimarySymptom, checked: boolean) => {
    const next = checked ? [...value.secondarySymptoms, symptom] : value.secondarySymptoms.filter((item) => item !== symptom)
    onChange({ ...value, secondarySymptoms: next })
  }
  return (
    <>
      <label>
        Main symptom
        <select
          value={value.primarySymptom}
          onChange={(event) => {
            const primary = event.target.value as PrimarySymptom | ''
            // The main symptom is not repeated among the other symptoms.
            onChange({ ...value, primarySymptom: primary, secondarySymptoms: value.secondarySymptoms.filter((item) => item !== primary) })
          }}
        >
          <option value="">Not decided yet</option>
          {PRIMARY_SYMPTOMS.map((symptom) => (
            <option key={symptom} value={symptom}>
              {PRIMARY_SYMPTOM_LABELS[symptom]}
            </option>
          ))}
        </select>
      </label>
      <fieldset>
        <legend>Other symptoms</legend>
        <div className="row">
          {PRIMARY_SYMPTOMS.filter((symptom) => symptom !== value.primarySymptom).map((symptom) => (
            <label key={symptom} className="check">
              <input
                type="checkbox"
                checked={value.secondarySymptoms.includes(symptom)}
                onChange={(event) => toggle(symptom, event.target.checked)}
              />
              {PRIMARY_SYMPTOM_LABELS[symptom]}
            </label>
          ))}
        </div>
      </fieldset>
      <label>
        When does the fault show?
        <select value={value.phase} onChange={(event) => onChange({ ...value, phase: event.target.value })}>
          <option value="">Not known</option>
          {Object.entries(TIMING_PHASE_LABELS).map(([phase, label]) => (
            <option key={phase} value={phase}>
              {label}
            </option>
          ))}
        </select>
      </label>
      {value.phase !== '' && (
        <label>
          Minutes after the start (optional)
          <input
            type="number"
            min={0}
            value={value.afterMinutes}
            onChange={(event) => onChange({ ...value, afterMinutes: event.target.value })}
          />
        </label>
      )}
    </>
  )
}
