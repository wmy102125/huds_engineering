# CR-002 — Multi-Visit Repairs, Parts Deposits & Settlement
**Priority:** P1 / Phase 1. **Owner:** Product Owner. **Technical Lead:** GPT-6. **Implementer:** Claude Code. **Independent Reviewer:** Gemini.

## 1. Source and boundaries
Business source: HUGO’S Work Order / Service Invoice v1.0, plus Appliance Diagnostic Data Record v4.32. v1.0 supports diagnostic/labor/parts, Amount Paid Today on Site, Remaining Balance Due, diagnostic authorization and completion acceptance, but has no dedicated parts deposit, payment history, order status or multiple visit fields. CR-001 v1.3 provides pre-service SMS-link agreement and appointment confirmation. Do not replace either source form without explicit approval. The included v1.1 printable form is a **design draft**, not an exact visual revision of the original.

## 2. User stories
- As technician, I open one case, record Visit #1 diagnosis, set WAITING_PARTS, then record Visit #2 installation/verification without overwriting earlier evidence.
- As customer, I receive an itemized versioned estimate showing total projected cost and deposit/refund terms before paying for a part.
- As owner, I receive a parts deposit, issue a receipt, place an order, track arrival, schedule next visit, apply the deposit against the final invoice and refund when required.
- As reviewer, I can reconstruct all estimate revisions, approvals, deposit transactions, supplier orders, visits, invoices and customer communications.

## 3. Required workflow
1. Case opened → Visit #1 planned/confirmed (CR-001) → technician records diagnosis and safety evidence.
2. Technician prepares Estimate Revision #1: diagnostic, labor, part lines, any applicable taxes, anticipated follow-up fee (if any), full projected total, ETA, deposit amount and cancellation/refund policy.
3. Customer explicitly approves that revision and parts-order scope; store immutable approval snapshot + method/time/evidence. A deposit request alone is not authorization.
4. Deposit payment is captured, classified `PARTS_DEPOSIT` and allocated to case/approved estimate; issue payment receipt; **do not treat the deposit as an extra charge**.
5. Parts order is placed (authorized supplier/part/quantity/cost, expected date); mark case `PENDING_PARTS`. Handle supplier cancellation, backorder, wrong/DOA part, part supersession and customer cancellation.
6. Receipt of part permits follow-up appointment; Visit #2 installation, new safety check, actual parts used, post-repair verification and customer completion acceptance.
7. Issue final invoice from actual billable lines; show total charges, previous payments/deposits, payment today, balance. Apply deposit exactly once. Link invoice/receipt to case; never overwrite an issued invoice (issue correction/credit as needed).
8. For unsuccessful repair, partial work, refunds or multiple follow-ups, preserve each visit and decision; closing case is independent of whether payment is settled.

## 4. Domain invariants
- `service_visits.case_id` is 1:N; chronological `visit_number` unique per case, never reused; no automatic extra call-out fee for second visit unless agreed.
- Quote approval binds to **exact revision/hash** and disclosed fees; changing scope/price requires new revision and approval.
- Parts order requires approved authorization for its price/scope, except an explicitly documented company-funded inventory procurement path.
- Parts order state and payment state are independent. Deposit policy must be explicitly presented; refunds follow applicable consumer law and agreed terms. Never assume all deposits non-refundable.
- Money stored as decimal numeric, CAD currency, UTC timestamps with local timezone rendering. No floating-point money.
- Ledger: immutable received/refunded transactions with signed amounts or explicit transaction kind; `payment_records` v1.3 is legacy/basic and cannot alone provide a complete ledger. Invoice balance = final invoice total − net allocated receipts (including deposits) + any lawful adjustments. Validate allocation totals ≤ net available funds; duplicate webhook/idempotency cannot double-credit.
- Customer pre-service consent under CR-001 is **not** a parts purchase/repair approval. Appointment agreed by phone is recorded as staff-reported unless customer explicitly confirmed.
- Issued invoice/receipt documents and authorization evidence have immutable snapshots and auditable corrections.
- Taxes are conditional on actual GST/HST registration and applicable rules; never hardcode 15% or assume tax collection without registration.

## 5. State machines (separate aggregates)
- Case: `OPEN`, `DIAGNOSING`, `PENDING_AUTHORIZATION`, `PENDING_PARTS`, `IN_REPAIR`, `VERIFYING`, `ON_HOLD`, `CLOSED`, `CANCELLED` (existing baseline). Case disposition separately.
- Visit: `PLANNED`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `CANCELLED` (existing); additional scheduling details belong to appointment aggregate.
- Estimate: `DRAFT`, `ISSUED`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `SUPERSEDED` (existing).
- Parts order: `DRAFT`, `AWAITING_APPROVAL`, `AWAITING_DEPOSIT`, `READY_TO_ORDER`, `ORDERED`, `BACKORDERED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`, `CLOSED`.
- Order line: `REQUESTED`, `ORDERED`, `BACKORDERED`, `RECEIVED`, `INSTALLED`, `RETURNED`, `CANCELLED`; DOA is a disposition/incident, not an automatic completed installation.
- Invoice: `DRAFT`, `ISSUED`, `VOIDED`, `CREDITED` (credits must be explicit documents).
- Payment: `PENDING`, `SETTLED`, `FAILED`, `REVERSED`; refund is a separate money movement.

## 6. Customer-visible documents
- Quote: estimate revision, all lines, estimated total, deposit amount, special-order status, ETA caveat, cancel/refund terms, approval method.
- Deposit receipt: receipt ID, timestamp, amount, method, applied-to case/order, remaining projected balance, and **not** proof of repair completion.
- Final invoice: line items, taxes if applicable, total charges, previous payments/deposits, payment received today, balance; visit reference and completion evidence.
- Change order / revised estimate for material changes; never silently change approved quote.

## 7. UX / offline rules
- Desktop and mobile: view visits, parts order timeline, payment timeline and outstanding balance; quick-create follow-up visit; clearly label amounts as estimates vs actuals.
- Offline: draft diagnoses and visit notes locally; financial actions (deposit capture, quote acceptance, parts order, invoice issuance, refunds) require online server validation and idempotent command handling. Never present a queued offline financial action as completed.
- Customer web link: CR-001 terms acceptance; separately obtain repair/parts estimate authorization tied to exact revision.

## 8. Implementation scope and sequencing
Sprint 0: retain existing baseline; add CR-002 ADR and acceptance cases. Sprint 2: multi-visit UI and case timeline. Sprint 4: parts orders, line items and supplier statuses. Sprint 5: estimate authorization, deposits, receipts, invoices, refunds and allocations; test vertical slice end-to-end. All migrations are additive and reversible by forward correction, not by editing previously applied V001–V003.

## 9. Open decisions requiring Product Owner approval
Diagnostic fee credit toward repair? second-visit call-out fee? deposit percentage and minimum? which payment methods actually accepted? special-order refund terms? invoice numbering scheme? tax registration status? These are **configuration/policy decisions**, not hardcoded defaults.
