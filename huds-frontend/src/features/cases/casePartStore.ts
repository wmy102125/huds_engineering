import type { CasePartPayload, PartsSupplyStatus } from '../../api/types'
import { db } from '../../db/db'
import type { LocalCase, LocalCasePart } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'
import { isFinished } from './caseLabels'

/**
 * The parts a case needs: the ones that are being waited for and the ones that are in hand.
 * Every part has a warranty (in months), which counts from the installation.
 */

export const PARTS_STATUS_LABELS: Record<PartsSupplyStatus, string> = {
  IN_STOCK: 'In stock',
  ORDERED: 'Ordered',
  BACKORDER: 'Backorder',
  CUSTOMER_SUPPLIED: 'Customer supplied',
  ARRIVED: 'Arrived',
}

/** A part that the repair still has to wait for. */
export function isPartWaiting(part: LocalCasePart): boolean {
  return part.partsSupplyStatus === 'ORDERED' || part.partsSupplyStatus === 'BACKORDER'
}

/** The form as the user fills it in: numbers and dates are still text. */
export interface CasePartForm {
  partName: string
  oemPartNo: string
  quantity: string
  unit: string
  supplier: string
  partsSupplyStatus: PartsSupplyStatus
  orderedOn: string
  expectedArrivalOn: string
  arrivedOn: string
  warrantyMonths: string
}

export const EMPTY_PART_FORM: CasePartForm = {
  partName: '',
  oemPartNo: '',
  quantity: '1',
  unit: '',
  supplier: '',
  partsSupplyStatus: 'ORDERED',
  orderedOn: '',
  expectedArrivalOn: '',
  arrivedOn: '',
  warrantyMonths: '',
}

export function formOfPart(part: LocalCasePart): CasePartForm {
  return {
    partName: part.partName,
    oemPartNo: part.oemPartNo,
    quantity: String(part.quantity),
    unit: part.unit,
    supplier: part.supplier,
    partsSupplyStatus: part.partsSupplyStatus,
    orderedOn: part.orderedOn ?? '',
    expectedArrivalOn: part.expectedArrivalOn ?? '',
    arrivedOn: part.arrivedOn ?? '',
    warrantyMonths: part.warrantyMonths === null ? '' : String(part.warrantyMonths),
  }
}

export async function listParts(diagnosticCaseId: string): Promise<LocalCasePart[]> {
  const rows = await db().caseParts.where('diagnosticCaseId').equals(diagnosticCaseId).toArray()
  // The parts that are still being waited for come first, then by name.
  return rows
    .filter((row) => !row.archiving)
    .sort((a, b) => Number(isPartWaiting(b)) - Number(isPartWaiting(a)) || a.partName.localeCompare(b.partName))
}

/** Checks the form like the server does and turns it into the body of the change. */
function toPayload(diagnosticCaseId: string, form: CasePartForm, installedVisitId: string | null): CasePartPayload {
  const partName = form.partName.trim()
  if (partName === '') {
    throw new Error('Please enter the name of the part.')
  }
  if (partName.length > 200) {
    throw new Error('The name is too long (200 characters at most).')
  }
  const quantity = Number(form.quantity.replace(',', '.'))
  if (form.quantity.trim() === '' || !Number.isFinite(quantity) || quantity <= 0) {
    throw new Error('The quantity must be more than 0.')
  }
  let warrantyMonths: number | null = null
  if (form.warrantyMonths.trim() !== '') {
    warrantyMonths = Number(form.warrantyMonths)
    if (!Number.isInteger(warrantyMonths) || warrantyMonths < 0) {
      throw new Error('The warranty must be a whole number of months (0 or more).')
    }
  }
  const oemPartNo = form.oemPartNo.trim()
  const unit = form.unit.trim()
  const supplier = form.supplier.trim()
  if (oemPartNo.length > 100 || unit.length > 50 || supplier.length > 200) {
    throw new Error('One of the texts is too long.')
  }
  const dateOrNull = (value: string) => (value === '' ? null : value)
  const orderedOn = dateOrNull(form.orderedOn)
  const expectedArrivalOn = dateOrNull(form.expectedArrivalOn)
  const arrivedOn = dateOrNull(form.arrivedOn)
  if (orderedOn && expectedArrivalOn && expectedArrivalOn < orderedOn) {
    throw new Error('The expected arrival cannot be before the order date.')
  }
  if (form.partsSupplyStatus === 'ARRIVED' && arrivedOn === null) {
    throw new Error('Please enter the day the part arrived.')
  }
  return {
    diagnosticCaseId,
    partName,
    oemPartNo,
    quantity,
    unit,
    supplier,
    partsSupplyStatus: form.partsSupplyStatus,
    orderedOn,
    expectedArrivalOn,
    arrivedOn,
    warrantyMonths,
    installedVisitId,
  }
}

function assertEditable(caseRecord: LocalCase): void {
  if (isFinished(caseRecord.workflowStatus)) {
    throw new Error('A closed case cannot be changed.')
  }
}

/** Adds a part to a case. It waits behind the case when the server does not have the case yet. */
export async function addPart(caseRecord: LocalCase, form: CasePartForm): Promise<void> {
  assertEditable(caseRecord)
  const payload = toPayload(caseRecord.diagnosticCaseId, form, null)
  const casePartId = crypto.randomUUID()
  const database = db()
  await database.transaction('rw', database.caseParts, database.outbox, async () => {
    await database.caseParts.add({
      casePartId,
      ...payload,
      installedVisitId: null,
      syncState: 'PENDING',
    })
    const caseCreate = await findCreateMutationId(caseRecord.diagnosticCaseId)
    await enqueueMutation({
      entityType: 'CASE_PART',
      entityId: casePartId,
      operation: 'CREATE',
      payload,
      dependsOn: caseCreate ? [caseCreate] : [],
    })
  })
}

/** Changes a part. The case of a part never changes. */
export async function updatePart(caseRecord: LocalCase, part: LocalCasePart, form: CasePartForm): Promise<void> {
  assertEditable(caseRecord)
  const payload = toPayload(part.diagnosticCaseId, form, part.installedVisitId)
  const database = db()
  await database.transaction('rw', database.caseParts, database.outbox, async () => {
    await database.caseParts.update(part.casePartId, { ...payload, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'CASE_PART',
      entityId: part.casePartId,
      operation: 'UPDATE',
      rowVersion: part.rowVersion,
      payload,
    })
  })
}

/** The part is in hand: status "Arrived", with today as the day it arrived (when no day was entered before). */
export async function markPartArrived(caseRecord: LocalCase, part: LocalCasePart): Promise<void> {
  const today = new Date().toLocaleDateString('en-CA')
  await updatePart(caseRecord, part, { ...formOfPart(part), partsSupplyStatus: 'ARRIVED', arrivedOn: part.arrivedOn ?? today })
}

/** Removes a part that was entered by mistake. */
export async function removePart(caseRecord: LocalCase, part: LocalCasePart): Promise<void> {
  assertEditable(caseRecord)
  const database = db()
  await database.transaction('rw', database.caseParts, database.outbox, async () => {
    const result = await enqueueMutation({
      entityType: 'CASE_PART',
      entityId: part.casePartId,
      operation: 'ARCHIVE',
      rowVersion: part.rowVersion,
    })
    if (result === 'DISCARDED') {
      await database.caseParts.delete(part.casePartId)
    } else {
      await database.caseParts.update(part.casePartId, { syncState: 'PENDING', archiving: true })
    }
  })
}

const ADD_PART_KEY = 'huds.addPartForCase'

/** Remembers that the parts dialog should open when the page of this case is shown next (after a visit that needs parts). */
export function requestAddPart(diagnosticCaseId: string): void {
  try {
    sessionStorage.setItem(ADD_PART_KEY, diagnosticCaseId)
  } catch {
    // Browser storage can be blocked: the person then simply uses the "+ Add part" button.
  }
}

/** True when {@link requestAddPart} was called for this case and has not been cleared yet. */
export function hasAddPartRequest(diagnosticCaseId: string): boolean {
  try {
    return sessionStorage.getItem(ADD_PART_KEY) === diagnosticCaseId
  } catch {
    return false
  }
}

export function clearAddPartRequest(): void {
  try {
    sessionStorage.removeItem(ADD_PART_KEY)
  } catch {
    // Nothing to clear.
  }
}
