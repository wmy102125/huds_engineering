import type { CaseErrorCodePayload, ErrorCodeStatus } from '../../api/types'
import { db } from '../../db/db'
import type { LocalCase, LocalCaseErrorCode } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'
import { isFinished } from './caseLabels'

/**
 * The error codes of a case. An error code is never deleted: when it is no longer shown on the appliance
 * its status is changed to STORED_HISTORY. The same code cannot be listed twice for one case (any letter case).
 */

export interface ErrorCodeForm {
  errorCode: string
  displayMessage: string
  codeStatus: ErrorCodeStatus
}

export async function listErrorCodes(diagnosticCaseId: string): Promise<LocalCaseErrorCode[]> {
  const rows = await db().caseErrorCodes.where('diagnosticCaseId').equals(diagnosticCaseId).toArray()
  // Codes that are shown now come first.
  return rows.sort((a, b) => a.codeStatus.localeCompare(b.codeStatus) || a.errorCode.localeCompare(b.errorCode))
}

function clean(form: ErrorCodeForm): ErrorCodeForm {
  const errorCode = form.errorCode.trim()
  if (errorCode === '') {
    throw new Error('Please enter the error code.')
  }
  if (errorCode.length > 100) {
    throw new Error('The error code is too long (100 characters at most).')
  }
  const displayMessage = form.displayMessage.trim()
  if (displayMessage.length > 500) {
    throw new Error('The message is too long (500 characters at most).')
  }
  return { errorCode, displayMessage, codeStatus: form.codeStatus }
}

function assertNotDuplicate(existing: LocalCaseErrorCode[], errorCode: string, ownId?: string): void {
  const same = existing.find((row) => row.caseErrorCodeId !== ownId && row.errorCode.toLowerCase() === errorCode.toLowerCase())
  if (same) {
    throw new Error(`The code ${same.errorCode} is already listed for this case.`)
  }
}

/** Adds an error code to an existing case. It waits behind the case when the server does not have the case yet. */
export async function addErrorCode(caseRecord: LocalCase, form: ErrorCodeForm): Promise<void> {
  if (isFinished(caseRecord.workflowStatus)) {
    throw new Error('A closed case cannot be changed.')
  }
  const values = clean(form)
  const database = db()
  await database.transaction('rw', database.caseErrorCodes, database.outbox, async () => {
    assertNotDuplicate(await listErrorCodes(caseRecord.diagnosticCaseId), values.errorCode)
    await insertErrorCode(caseRecord.diagnosticCaseId, values)
  })
}

/** Must run inside a transaction on caseErrorCodes and outbox. Also used when a case is opened together with its codes. */
export async function insertErrorCode(diagnosticCaseId: string, values: ErrorCodeForm): Promise<void> {
  const caseErrorCodeId = crypto.randomUUID()
  const payload: CaseErrorCodePayload = { diagnosticCaseId, ...values }
  await db().caseErrorCodes.add({ caseErrorCodeId, ...payload, syncState: 'PENDING' })
  const caseCreate = await findCreateMutationId(diagnosticCaseId)
  await enqueueMutation({
    entityType: 'CASE_ERROR_CODE',
    entityId: caseErrorCodeId,
    operation: 'CREATE',
    payload,
    dependsOn: caseCreate ? [caseCreate] : [],
  })
}

/** Changes the code, the message or the status of an error code. */
export async function updateErrorCode(caseRecord: LocalCase, row: LocalCaseErrorCode, form: ErrorCodeForm): Promise<void> {
  if (isFinished(caseRecord.workflowStatus)) {
    throw new Error('A closed case cannot be changed.')
  }
  const values = clean(form)
  const payload: CaseErrorCodePayload = { diagnosticCaseId: row.diagnosticCaseId, ...values }
  const database = db()
  await database.transaction('rw', database.caseErrorCodes, database.outbox, async () => {
    assertNotDuplicate(await listErrorCodes(row.diagnosticCaseId), values.errorCode, row.caseErrorCodeId)
    await database.caseErrorCodes.update(row.caseErrorCodeId, { ...values, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'CASE_ERROR_CODE',
      entityId: row.caseErrorCodeId,
      operation: 'UPDATE',
      rowVersion: row.rowVersion,
      payload,
    })
  })
}
