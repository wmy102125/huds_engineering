import { hasPermission, PERMISSION } from '../../api/permissions'
import type {
  CaseInvoicePaymentPayload,
  CaseInvoicePayload,
  CurrentUser,
  InvoiceStatus,
  PaymentMethod,
} from '../../api/types'
import { db } from '../../db/db'
import type { LocalCase, LocalCaseInvoice, LocalCaseInvoicePayment } from '../../db/db'
import { enqueueMutation, findCreateMutationId } from '../../sync/outbox'

/**
 * The bill of a case and the payments received for it. Amounts are in CAD.
 * A case has one bill. The fees can be changed while the bill is a draft; an issued bill can only be voided.
 * Payments can be added and corrected until the bill is void. Nothing is deleted.
 */

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  DEBIT_CARD: 'Debit card',
  CREDIT_CARD: 'Credit card',
  E_TRANSFER: 'E-transfer',
  CHEQUE: 'Cheque',
}

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Draft',
  ISSUED: 'Issued',
  VOID: 'Void',
}

export function getInvoice(diagnosticCaseId: string): Promise<LocalCaseInvoice | undefined> {
  return db().caseInvoices.where('diagnosticCaseId').equals(diagnosticCaseId).first()
}

export async function listPayments(caseInvoiceId: string): Promise<LocalCaseInvoicePayment[]> {
  const rows = await db().caseInvoicePayments.where('caseInvoiceId').equals(caseInvoiceId).toArray()
  return rows.sort((a, b) => a.receivedAt.localeCompare(b.receivedAt))
}

// ---- Amounts ----

/** Works in whole cents so that adding amounts never shows 0.30000000000000004. */
function cents(amount: number): number {
  return Math.round(amount * 100)
}

export interface InvoiceTotals {
  subtotal: number
  tax: number
  total: number
}

/** The same sums the server uses: tax is rounded to the cent. */
export function invoiceTotals(invoice: Pick<LocalCaseInvoice, 'serviceCallFee' | 'labourFee' | 'partsFee' | 'discountAmount' | 'taxRate'>): InvoiceTotals {
  const subtotal = cents(invoice.serviceCallFee) + cents(invoice.labourFee) + cents(invoice.partsFee)
  const taxable = subtotal - cents(invoice.discountAmount)
  const tax = Math.round(taxable * invoice.taxRate)
  return { subtotal: subtotal / 100, tax: tax / 100, total: (taxable + tax) / 100 }
}

export function totalPaid(payments: LocalCaseInvoicePayment[]): number {
  return payments.reduce((sum, row) => sum + cents(row.amount), 0) / 100
}

export function money(amount: number): string {
  return `$${amount.toFixed(2)}`
}

/** An amount typed by the user: empty means 0; anything that is not an amount with at most two decimals is refused. */
function readMoney(text: string, label: string): number {
  const trimmed = text.trim()
  if (trimmed === '') {
    return 0
  }
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(trimmed)) {
    throw new Error(`${label} must be an amount like 45 or 45.50.`)
  }
  return Number(trimmed)
}

// ---- The bill ----

export interface InvoiceForm {
  serviceCallFee: string
  labourFee: string
  partsFee: string
  discountAmount: string
  /** Percent, for example 15 for 15 %. Empty means no tax. */
  taxPercent: string
}

export function invoiceFormFrom(invoice: LocalCaseInvoice | undefined): InvoiceForm {
  const text = (amount: number | undefined) => (amount === undefined || amount === 0 ? '' : String(amount))
  return {
    serviceCallFee: text(invoice?.serviceCallFee),
    labourFee: text(invoice?.labourFee),
    partsFee: text(invoice?.partsFee),
    discountAmount: text(invoice?.discountAmount),
    taxPercent: invoice ? String(Math.round(invoice.taxRate * 10000) / 100) : '',
  }
}

/** Why the bill cannot be changed now, or null when it can. */
export function invoiceBlockedReason(user: CurrentUser): string | null {
  return hasPermission(user, PERMISSION.CASE_WRITE) ? null : 'You are not allowed to change the bill of a case.'
}

function readFees(form: InvoiceForm) {
  const serviceCallFee = readMoney(form.serviceCallFee, 'The service call fee')
  const labourFee = readMoney(form.labourFee, 'The labour fee')
  const partsFee = readMoney(form.partsFee, 'The parts fee')
  const discountAmount = readMoney(form.discountAmount, 'The discount')
  const percentText = form.taxPercent.trim()
  if (percentText !== '' && !/^\d{1,2}(\.\d{1,2})?$/.test(percentText)) {
    throw new Error('The tax must be a percentage like 15 or 14.5.')
  }
  const taxRate = percentText === '' ? 0 : Math.round(Number(percentText) * 100) / 10000
  if (cents(discountAmount) > cents(serviceCallFee) + cents(labourFee) + cents(partsFee)) {
    throw new Error('The discount cannot be more than the subtotal.')
  }
  return { serviceCallFee, labourFee, partsFee, discountAmount, taxRate }
}

/** Creates the bill of a case (as a draft) or changes the fees of a draft. */
export async function saveInvoice(caseRecord: LocalCase, user: CurrentUser, existing: LocalCaseInvoice | undefined, form: InvoiceForm): Promise<void> {
  const blocked = invoiceBlockedReason(user)
  if (blocked) {
    throw new Error(blocked)
  }
  if (existing && existing.invoiceStatus !== 'DRAFT') {
    throw new Error('The fees of a bill that is issued or void cannot be changed.')
  }
  const fees = readFees(form)
  const payload: CaseInvoicePayload = {
    diagnosticCaseId: caseRecord.diagnosticCaseId,
    invoiceStatus: null,
    issuedAt: null,
    ...fees,
  }
  const database = db()
  await database.transaction('rw', database.caseInvoices, database.outbox, async () => {
    if (existing) {
      await database.caseInvoices.update(existing.caseInvoiceId, { ...fees, syncState: 'PENDING' })
      await enqueueMutation({
        entityType: 'CASE_INVOICE',
        entityId: existing.caseInvoiceId,
        operation: 'UPDATE',
        rowVersion: existing.rowVersion,
        payload,
      })
      return
    }
    const caseInvoiceId = crypto.randomUUID()
    await database.caseInvoices.add({
      caseInvoiceId,
      diagnosticCaseId: caseRecord.diagnosticCaseId,
      invoiceStatus: 'DRAFT',
      issuedAt: null,
      ...fees,
      syncState: 'PENDING',
    })
    // It waits behind the case when the server does not have the case yet.
    const caseCreate = await findCreateMutationId(caseRecord.diagnosticCaseId)
    await enqueueMutation({
      entityType: 'CASE_INVOICE',
      entityId: caseInvoiceId,
      operation: 'CREATE',
      payload,
      dependsOn: caseCreate ? [caseCreate] : [],
    })
  })
}

/** Issues or voids the bill. The server only allows DRAFT -> ISSUED, DRAFT -> VOID and ISSUED -> VOID. */
export async function changeInvoiceStatus(user: CurrentUser, invoice: LocalCaseInvoice, to: 'ISSUED' | 'VOID'): Promise<void> {
  const blocked = invoiceBlockedReason(user)
  if (blocked) {
    throw new Error(blocked)
  }
  if (invoice.invoiceStatus === 'VOID' || (invoice.invoiceStatus === 'ISSUED' && to === 'ISSUED')) {
    throw new Error(`The bill is already ${INVOICE_STATUS_LABELS[invoice.invoiceStatus].toLowerCase()}.`)
  }
  // A new bill is always created as a draft, so the server has to know the bill before its status can change.
  if (invoice.rowVersion === undefined) {
    throw new Error('The bill has not reached the server yet. Try again when it has been synced.')
  }
  const issuedAt = to === 'ISSUED' ? new Date().toISOString() : null
  const payload: CaseInvoicePayload = {
    diagnosticCaseId: invoice.diagnosticCaseId,
    invoiceStatus: to,
    issuedAt,
    serviceCallFee: invoice.serviceCallFee,
    labourFee: invoice.labourFee,
    partsFee: invoice.partsFee,
    discountAmount: invoice.discountAmount,
    taxRate: invoice.taxRate,
  }
  const database = db()
  await database.transaction('rw', database.caseInvoices, database.outbox, async () => {
    await database.caseInvoices.update(invoice.caseInvoiceId, {
      invoiceStatus: to,
      issuedAt: to === 'ISSUED' ? issuedAt : invoice.issuedAt,
      syncState: 'PENDING',
    })
    await enqueueMutation({
      entityType: 'CASE_INVOICE',
      entityId: invoice.caseInvoiceId,
      operation: 'UPDATE',
      rowVersion: invoice.rowVersion,
      payload,
    })
  })
}

// ---- Payments ----

export interface PaymentForm {
  amount: string
  paymentMethod: PaymentMethod
  referenceNo: string
  /** For an input of type datetime-local: "2026-11-10T14:30". */
  receivedAt: string
}

/** The time as an input of type datetime-local wants it (local time, no seconds). */
export function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function emptyPaymentForm(): PaymentForm {
  return { amount: '', paymentMethod: 'E_TRANSFER', referenceNo: '', receivedAt: toLocalInput(new Date().toISOString()) }
}

export function paymentFormFrom(row: LocalCaseInvoicePayment): PaymentForm {
  return {
    amount: String(row.amount),
    paymentMethod: row.paymentMethod,
    referenceNo: row.referenceNo,
    receivedAt: toLocalInput(row.receivedAt),
  }
}

function paymentBlockedReason(user: CurrentUser, invoice: LocalCaseInvoice): string | null {
  return invoiceBlockedReason(user) ?? (invoice.invoiceStatus === 'VOID' ? 'The bill is void, so its payments can no longer be changed.' : null)
}

function readPayment(form: PaymentForm): Omit<CaseInvoicePaymentPayload, 'caseInvoiceId'> {
  const amount = readMoney(form.amount, 'The amount')
  if (amount <= 0) {
    throw new Error('The amount must be more than 0.')
  }
  const referenceNo = form.referenceNo.trim()
  if (referenceNo.length > 100) {
    throw new Error('The reference is too long (100 characters at most).')
  }
  const received = new Date(form.receivedAt)
  if (form.receivedAt === '' || Number.isNaN(received.getTime())) {
    throw new Error('Please enter when the payment was received.')
  }
  return { amount, paymentMethod: form.paymentMethod, referenceNo, receivedAt: received.toISOString() }
}

/** Adds a payment to the bill. It waits behind the bill when the server does not have the bill yet. */
export async function addPayment(user: CurrentUser, invoice: LocalCaseInvoice, form: PaymentForm): Promise<void> {
  const blocked = paymentBlockedReason(user, invoice)
  if (blocked) {
    throw new Error(blocked)
  }
  const payload: CaseInvoicePaymentPayload = { caseInvoiceId: invoice.caseInvoiceId, ...readPayment(form) }
  const caseInvoicePaymentId = crypto.randomUUID()
  const database = db()
  await database.transaction('rw', database.caseInvoicePayments, database.outbox, async () => {
    await database.caseInvoicePayments.add({ caseInvoicePaymentId, ...payload, syncState: 'PENDING' })
    const invoiceCreate = await findCreateMutationId(invoice.caseInvoiceId)
    await enqueueMutation({
      entityType: 'CASE_INVOICE_PAYMENT',
      entityId: caseInvoicePaymentId,
      operation: 'CREATE',
      payload,
      dependsOn: invoiceCreate ? [invoiceCreate] : [],
    })
  })
}

/** Corrects a payment. The bill of a payment never changes. */
export async function updatePayment(user: CurrentUser, invoice: LocalCaseInvoice, row: LocalCaseInvoicePayment, form: PaymentForm): Promise<void> {
  const blocked = paymentBlockedReason(user, invoice)
  if (blocked) {
    throw new Error(blocked)
  }
  const values = readPayment(form)
  const payload: CaseInvoicePaymentPayload = { caseInvoiceId: row.caseInvoiceId, ...values }
  const database = db()
  await database.transaction('rw', database.caseInvoicePayments, database.outbox, async () => {
    await database.caseInvoicePayments.update(row.caseInvoicePaymentId, { ...values, syncState: 'PENDING' })
    await enqueueMutation({
      entityType: 'CASE_INVOICE_PAYMENT',
      entityId: row.caseInvoicePaymentId,
      operation: 'UPDATE',
      rowVersion: row.rowVersion,
      payload,
    })
  })
}
