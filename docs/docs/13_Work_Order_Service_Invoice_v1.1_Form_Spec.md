# HUGO’S Work Order / Service Invoice v1.1 — Revision Specification
**Status:** Proposed. **Source:** Work Order / Service Invoice v1.0 production PDF (2 pages, US Letter). **No change** to Diagnostic Data Record v4.32.

## Preserve
Business identity, contact, customer/appliance, diagnostic notes, service performed, fees, payment methods, 90-day limited warranty (subject to actual existing terms), customer signatures, and two-page letter layout. Do not silently rewrite existing legal terms.

## Changes to first page
1. Header: `Case / WO #`, `Visit #`, `Visit Date` (invoice number should be distinct when issued).
2. Service progress: `Diagnosis Only`, `Awaiting Parts`, `Follow-up Required`, `Repair Completed`; status is a snapshot, not a warranty that the issue is solved.
3. Parts: `To Order`, `Ordered`, `Backordered`, `Received`, `Installed`, `Returned/Cancelled` (multiple parts tracked in HADS; form displays summary).
4. Billing: Diagnostic Fee; Labor; Parts & Materials; Subtotal; Tax (if applicable); **TOTAL CHARGES**; **PREVIOUS PAYMENTS / DEPOSITS**; **PAYMENT RECEIVED TODAY**; **BALANCE DUE**. No duplicate charging of deposits. Use formula: Balance = Total Charges - Previous Payments/Deposits - Payment Today, with credit/refund adjustments shown on supporting documents.
5. Customer authorization: A Diagnostic Authorization (or electronic Agreement Reference); B Parts Order & Repair Approval (approved estimate revision, estimated total, deposit amount, cancellation/refund policy reference); C Work Completion Acceptance (sign only when actually completed).
6. Reference: `Estimate # / Rev`, `Agreement Ref`, `Deposit Receipt Ref` and `Follow-up Visit` where space permits.

## Page 2
Preserve existing Terms & Conditions as source unless reviewed; add a narrowly worded reference to approved parts-order estimate and cancellation/refund policy. Any legal changes need local review before publication. Avoid blanket liability waivers or universal non-refundable deposit claims.

## Printing and accounting rules
- A prepayment receipt is issued when funds are received, not a final invoice.
- For multi-visit cases, issue a final invoice after service reaches appropriate billing milestone; if a diagnostic-only invoice is issued earlier, avoid rebilling that charge on the final invoice without explicit credit/reconciliation.
- Never show `HST` as charged unless the business is registered/required and the tax is applicable. `Tax (if applicable)` is safe as a placeholder; actual invoice must show legally required tax details.
- v1.1 draft form in `forms/` is **not** an exact graphical modification of v1.0 production PDF; proof against v1.0 branding and terms before use.
