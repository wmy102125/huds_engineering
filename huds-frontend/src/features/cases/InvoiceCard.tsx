import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import type { CurrentUser, PaymentMethod } from '../../api/types'
import { AuditLine } from '../../components/AuditLine'
import { SyncBadge } from '../../components/SyncBadge'
import type { LocalCase, LocalCaseInvoice, LocalCaseInvoicePayment } from '../../db/db'
import { Modal } from '../my/Modal'
import {
  addPayment,
  changeInvoiceStatus,
  emptyPaymentForm,
  INVOICE_STATUS_LABELS,
  invoiceBlockedReason,
  invoiceFormFrom,
  invoiceTotals,
  money,
  PAYMENT_METHOD_LABELS,
  paymentFormFrom,
  saveInvoice,
  totalPaid,
  updatePayment,
} from './invoiceStore'
import type { InvoiceForm, PaymentForm } from './invoiceStore'

interface Props {
  caseRecord: LocalCase
  user: CurrentUser
  invoice: LocalCaseInvoice | undefined
  payments: LocalCaseInvoicePayment[]
  onSaved: () => void
}

/** The bill of the case and the payments received for it. */
export function InvoiceCard({ caseRecord, user, invoice, payments, onSaved }: Props) {
  const blocked = invoiceBlockedReason(user)
  const refused = invoice?.syncState === 'REJECTED'
  const [feesDialog, setFeesDialog] = useState(false)
  const [paymentDialog, setPaymentDialog] = useState<{ row?: LocalCaseInvoicePayment } | null>(null)
  const [statusDialog, setStatusDialog] = useState<'ISSUED' | 'VOID' | null>(null)

  const done = () => {
    setFeesDialog(false)
    setPaymentDialog(null)
    setStatusDialog(null)
    onSaved()
  }

  if (!invoice) {
    return (
      <div className="card">
        <h3>Bill</h3>
        <p className="muted">{blocked ?? 'There is no bill for this case yet.'}</p>
        {blocked === null && (
          <div className="row">
            <button type="button" onClick={() => setFeesDialog(true)}>
              Create bill
            </button>
          </div>
        )}
        {feesDialog && <FeesModal caseRecord={caseRecord} user={user} invoice={undefined} onClose={() => setFeesDialog(false)} onSaved={done} />}
      </div>
    )
  }

  const totals = invoiceTotals(invoice)
  const paid = totalPaid(payments)
  const balance = Math.round((totals.total - paid) * 100) / 100
  const draft = invoice.invoiceStatus === 'DRAFT'
  const void_ = invoice.invoiceStatus === 'VOID'

  return (
    <div className="card">
      <div className="page-head">
        <h3>
          Bill <span className="badge">{INVOICE_STATUS_LABELS[invoice.invoiceStatus]}</span> <SyncBadge state={invoice.syncState} />
        </h3>
      </div>
      <dl className="facts">
        <dt>Service call &amp; diagnostic fee</dt>
        <dd>{money(invoice.serviceCallFee)}</dd>
        <dt>Labour</dt>
        <dd>{money(invoice.labourFee)}</dd>
        <dt>Parts</dt>
        <dd>{money(invoice.partsFee)}</dd>
        <dt>Subtotal</dt>
        <dd>{money(totals.subtotal)}</dd>
        <dt>Discount</dt>
        <dd>-{money(invoice.discountAmount)}</dd>
        <dt>Tax ({Math.round(invoice.taxRate * 10000) / 100}%)</dt>
        <dd>{money(totals.tax)}</dd>
        <dt>
          <strong>Total</strong>
        </dt>
        <dd>
          <strong>{money(totals.total)}</strong>
        </dd>
        <dt>Paid</dt>
        <dd>{money(paid)}</dd>
        <dt>Balance</dt>
        <dd>{money(balance)}</dd>
        {invoice.issuedAt && (
          <>
            <dt>Issued</dt>
            <dd>{new Date(invoice.issuedAt).toLocaleString()}</dd>
          </>
        )}
      </dl>
      <AuditLine record={invoice} />
      {refused && <p className="notice">The server refused a change to the bill. See "Needs attention" at the top of the page.</p>}
      {blocked === null && !refused && !void_ && (
        <div className="row">
          {draft && (
            <button type="button" onClick={() => setFeesDialog(true)}>
              Edit fees
            </button>
          )}
          {draft && (
            <button type="button" className="primary" onClick={() => setStatusDialog('ISSUED')}>
              Issue bill
            </button>
          )}
          <button type="button" onClick={() => setStatusDialog('VOID')}>
            Void bill
          </button>
        </div>
      )}

      <div className="page-head">
        <h4>Payments</h4>
        {blocked === null && !void_ && !refused && (
          <button type="button" onClick={() => setPaymentDialog({})}>
            + Add payment
          </button>
        )}
      </div>
      {payments.length === 0 ? (
        <p className="muted">No payment received yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Received</th>
                <th>Amount</th>
                <th>Method</th>
                <th>Reference</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {payments.map((row) => (
                <tr key={row.caseInvoicePaymentId}>
                  <td data-label="Received">
                    {new Date(row.receivedAt).toLocaleString()} <SyncBadge state={row.syncState} />
                  </td>
                  <td data-label="Amount">{money(row.amount)}</td>
                  <td data-label="Method">{PAYMENT_METHOD_LABELS[row.paymentMethod]}</td>
                  <td data-label="Reference">{row.referenceNo}</td>
                  <td>
                    {blocked === null && !void_ && !refused && (
                      <button type="button" disabled={row.syncState === 'REJECTED'} onClick={() => setPaymentDialog({ row })}>
                        Edit
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {feesDialog && <FeesModal caseRecord={caseRecord} user={user} invoice={invoice} onClose={() => setFeesDialog(false)} onSaved={done} />}
      {paymentDialog && (
        <PaymentModal user={user} invoice={invoice} row={paymentDialog.row} onClose={() => setPaymentDialog(null)} onSaved={done} />
      )}
      {statusDialog && <StatusModal user={user} invoice={invoice} to={statusDialog} onClose={() => setStatusDialog(null)} onSaved={done} />}
    </div>
  )
}

interface FeesProps {
  caseRecord: LocalCase
  user: CurrentUser
  invoice: LocalCaseInvoice | undefined
  onClose: () => void
  onSaved: () => void
}

function FeesModal({ caseRecord, user, invoice, onClose, onSaved }: FeesProps) {
  const [form, setForm] = useState<InvoiceForm>(invoiceFormFrom(invoice))
  const [error, setError] = useState<string | null>(null)
  const set = (key: keyof InvoiceForm, value: string) => setForm((previous) => ({ ...previous, [key]: value }))

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      await saveInvoice(caseRecord, user, invoice, form)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The bill could not be saved.')
      return
    }
    onSaved()
  }

  const field = (key: keyof InvoiceForm, label: string, autoFocus = false) => (
    <label>
      {label}
      <input value={form[key]} onChange={(event) => set(key, event.target.value)} inputMode="decimal" autoComplete="off" autoFocus={autoFocus} />
    </label>
  )

  return (
    <Modal title={invoice ? 'Edit fees' : 'Create bill'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        {field('serviceCallFee', 'Service call & diagnostic fee ($)', true)}
        {field('labourFee', 'Labour ($)')}
        {field('partsFee', 'Parts ($)')}
        {field('discountAmount', 'Discount ($)')}
        {field('taxPercent', 'Tax (%, leave empty for none)')}
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            Save
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface PaymentProps {
  user: CurrentUser
  invoice: LocalCaseInvoice
  row?: LocalCaseInvoicePayment
  onClose: () => void
  onSaved: () => void
}

function PaymentModal({ user, invoice, row, onClose, onSaved }: PaymentProps) {
  const [form, setForm] = useState<PaymentForm>(row ? paymentFormFrom(row) : emptyPaymentForm())
  const [error, setError] = useState<string | null>(null)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    try {
      if (row) {
        await updatePayment(user, invoice, row, form)
      } else {
        await addPayment(user, invoice, form)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The payment could not be saved.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={row ? 'Edit payment' : 'Add payment'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Amount ($)
          <input value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} inputMode="decimal" autoComplete="off" autoFocus />
        </label>
        <label>
          Method
          <select value={form.paymentMethod} onChange={(event) => setForm({ ...form, paymentMethod: event.target.value as PaymentMethod })}>
            {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
              <option key={method} value={method}>
                {PAYMENT_METHOD_LABELS[method]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Reference (cheque number, e-transfer reference; optional)
          <input value={form.referenceNo} onChange={(event) => setForm({ ...form, referenceNo: event.target.value })} autoComplete="off" maxLength={100} />
        </label>
        <label>
          Received
          <input type="datetime-local" value={form.receivedAt} onChange={(event) => setForm({ ...form, receivedAt: event.target.value })} />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary">
            Save
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface StatusProps {
  user: CurrentUser
  invoice: LocalCaseInvoice
  to: 'ISSUED' | 'VOID'
  onClose: () => void
  onSaved: () => void
}

/** Issuing and voiding cannot be undone, so they ask once more. */
function StatusModal({ user, invoice, to, onClose, onSaved }: StatusProps) {
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setError(null)
    try {
      await changeInvoiceStatus(user, invoice, to)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'The bill could not be changed.')
      return
    }
    onSaved()
  }

  return (
    <Modal title={to === 'ISSUED' ? 'Issue bill' : 'Void bill'} onClose={onClose}>
      <div className="stack">
        <p>
          {to === 'ISSUED'
            ? 'After the bill is issued its fees can no longer be changed. It can only be voided.'
            : 'A void bill cannot be changed again, and no more payments can be recorded on it.'}
        </p>
        {error && <p className="error">{error}</p>}
        <div className="row end">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="primary" onClick={() => void handleConfirm()}>
            {to === 'ISSUED' ? 'Issue bill' : 'Void bill'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
