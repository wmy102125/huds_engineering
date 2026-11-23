import type { CurrentUser, DiagnosticStepPayload, PowerState, StepResult } from '../../api/types'
import { db } from '../../db/db'
import type { LocalDiagnosticStep, LocalVisit } from '../../db/db'
import { enqueueMutation, findLastUnsentMutationId } from '../../sync/outbox'
import { visitRecordsBlockedReason } from './visitRecordGuard'

/**
 * The diagnostic steps of a visit (section 3 of the diagnostic form): one row per hypothesis that was tested.
 * Steps are numbered from 1 within the visit. They are written while the visit is in progress or paused.
 * A step is never deleted: a wrong step is corrected by editing it.
 */

export interface StepForm {
  hypothesis: string
  testTarget: string
  powerState: '' | PowerState
  expected: string
  actual: string
  result: '' | StepResult
  nextStep: string
}

export const EMPTY_STEP_FORM: StepForm = {
  hypothesis: '',
  testTarget: '',
  powerState: '',
  expected: '',
  actual: '',
  result: '',
  nextStep: '',
}

export function formOfStep(row: LocalDiagnosticStep): StepForm {
  return {
    hypothesis: row.hypothesis,
    testTarget: row.testTarget,
    powerState: row.powerState ?? '',
    expected: row.expected,
    actual: row.actual,
    result: row.result ?? '',
    nextStep: row.nextStep,
  }
}

export async function listSteps(serviceVisitId: string): Promise<LocalDiagnosticStep[]> {
  const rows = await db().diagnosticSteps.where('serviceVisitId').equals(serviceVisitId).toArray()
  return rows.sort((a, b) => a.stepNo - b.stepNo)
}

function tooLong(text: string, max: number, label: string): void {
  if (text.length > max) {
    throw new Error(`${label} is too long (${max} characters at most).`)
  }
}

/** Trims the text and refuses what the server would refuse. */
function clean(form: StepForm): Omit<DiagnosticStepPayload, 'serviceVisitId' | 'stepNo'> {
  const hypothesis = form.hypothesis.trim()
  if (hypothesis === '') {
    throw new Error('Please describe the hypothesis (the possible cause you are testing).')
  }
  const testTarget = form.testTarget.trim()
  const expected = form.expected.trim()
  const actual = form.actual.trim()
  const nextStep = form.nextStep.trim()
  tooLong(hypothesis, 1000, 'The hypothesis')
  tooLong(testTarget, 500, 'The test')
  tooLong(expected, 200, 'The expected value')
  tooLong(actual, 200, 'The actual value')
  tooLong(nextStep, 1000, 'The next step')
  return {
    hypothesis,
    testTarget,
    powerState: form.powerState === '' ? null : form.powerState,
    expected,
    actual,
    result: form.result === '' ? null : form.result,
    nextStep,
  }
}

function assertWritable(visit: LocalVisit, user: CurrentUser): void {
  const blocked = visitRecordsBlockedReason(visit, user)
  if (blocked) {
    throw new Error(blocked)
  }
}

/** Adds the next step of the visit. It waits behind the newest unsent change of the visit. */
export async function addStep(visit: LocalVisit, user: CurrentUser, form: StepForm): Promise<void> {
  assertWritable(visit, user)
  const values = clean(form)
  const database = db()
  await database.transaction('rw', database.diagnosticSteps, database.outbox, async () => {
    const existing = await listSteps(visit.serviceVisitId)
    const stepNo = (existing.at(-1)?.stepNo ?? 0) + 1
    const diagnosticStepId = crypto.randomUUID()
    const payload: DiagnosticStepPayload = { serviceVisitId: visit.serviceVisitId, stepNo, ...values }
    await database.diagnosticSteps.add({ diagnosticStepId, ...payload, syncState: 'PENDING' })
    // The server accepts steps only while the visit is on site, so they must come after the start of the visit.
    const lastVisitChange = await findLastUnsentMutationId('SERVICE_VISIT', visit.serviceVisitId)
    await enqueueMutation({
      entityType: 'DIAGNOSTIC_STEP',
      entityId: diagnosticStepId,
      operation: 'CREATE',
      payload,
      dependsOn: lastVisitChange ? [lastVisitChange] : [],
    })
  })
}

/** Changes a step. The visit and the number of a step never change. */
export async function updateStep(visit: LocalVisit, user: CurrentUser, row: LocalDiagnosticStep, form: StepForm): Promise<void> {
  assertWritable(visit, user)
  const values = clean(form)
  const payload: DiagnosticStepPayload = { serviceVisitId: row.serviceVisitId, stepNo: row.stepNo, ...values }
  const database = db()
  await database.transaction('rw', database.diagnosticSteps, database.outbox, async () => {
    await database.diagnosticSteps.update(row.diagnosticStepId, { ...values, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'DIAGNOSTIC_STEP',
      entityId: row.diagnosticStepId,
      operation: 'UPDATE',
      rowVersion: row.rowVersion,
      payload,
    })
  })
}
