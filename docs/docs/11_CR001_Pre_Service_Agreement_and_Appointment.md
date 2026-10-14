# CR-001 — Pre-Service Pricing Disclosure, Consent & Appointment Confirmation
**Status:** Approved for Phase 1 design baseline; legal wording and actual prices pending business/legal validation. **Priority:** P1; required before billing/authorization acceptance. **Business owner:** HUGO’S APPLIANCE REPAIR.

## Source and scope
Extends the two existing Phase 1 source forms: **Work Order / Service Invoice v1.0** (fees, customer and appliance, authorization, payment, invoice) and **Appliance Diagnostic Data Record v4.32** (safety, tests, evidence, diagnosis). Neither PDF needs editing to introduce the web acknowledgment flow. The HADS web flow is a separate digital record linked to the same service case/work order. This CR adds minimal appointment confirmation, not full dispatch/calendar software.

## Business rules
1. After a customer contacts the business, create a case/work order and send an individual, time-limited SMS link to the **public-facing pricing and terms page with the applicable fee snapshot**. Public `/rates` is informational; per-case `/confirm/{token}` records consent.
2. Show the exact applicable service call/diagnostic fee (or unambiguous calculation method), when it becomes payable, travel fees if any, cancellation/rescheduling terms, parts/labor policy, applicable tax disclosure, limited warranty and reasonable limitations. **Do not assert HST is charged unless the business is registered/required and tax is applicable.** Do not advertise unsupported payment methods or unreviewed blanket liability waivers.
3. Customer must actively check an initially unchecked box and press **I Agree & Confirm**. Mere SMS delivery, link opening, reading, or appointment scheduling alone does **not** prove assent. Do not use prechecked boxes or silent consent. A phone agreement can be recorded by staff as **staff-attested**, clearly differentiated from self-service web acceptance.
4. **Separate evidence events:** POLICY_ACCEPTED (specific policy and fee snapshot) and APPOINTMENT_CONFIRMED (time window, timezone, method, actor). Appointment may be arranged before or after policy acceptance. A case is `READY_FOR_VISIT` only when both are valid, linked, and consistent. A time booking by itself is not consent.
5. Accepted pre-service terms authorize **only the disclosed diagnostic visit scope**, if wording explicitly grants that authorization. Additional repair work, parts, or materially changed fees require a separate quote/version and affirmative repair authorization. Existing on-site diagnostic authorization may still be used as fallback when web consent is missing or inadequate.
6. Changes to material price/terms invalidate the affected prior acceptance and require re-acceptance; time-only rescheduling normally does not. Declined/expired/unconfirmed appointments are flagged for staff follow-up, not silently authorized.
7. Preserve the exact displayed policy/fee snapshot, SHA-256 hash, consent statement version, timestamps in UTC, method, associated case/customer, and append-only event/audit trail. Retain only necessary personal data; IP/user agent optional subject to privacy notice, retention/access controls.
8. Token: cryptographically random, single-purpose, scoped to case, expires, stored hashed; rate-limited endpoints; prevent token leakage through analytics/referrers; replay/idempotency safe. A link recipient is not necessarily the named customer; don't claim identity verification without additional verification.
9. SMS sending status is separate from consent status. Provide an accessible confirmation receipt to customer and a visible timeline on desktop/mobile. Online consent submission requires server acknowledgment; offline field staff may record pending staff-attested phone consent but must not label it web-verified.
10. Customer-facing sample invoice must be prominently watermarked **SAMPLE**, must not expose real customer information, and must not misrepresent a final amount.

## State model
- Policy acknowledgment: `PENDING | ACCEPTED | DECLINED | EXPIRED | SUPERSEDED` (append-only events; projection is mutable).
- Appointment: `PROPOSED | CONFIRMED | RESCHEDULED | CANCELLED`.
- Visit readiness: `READY_FOR_VISIT` iff valid accepted diagnostic scope + current fee snapshot + confirmed appointment; otherwise `ACTION_REQUIRED`.
- Repair authorization: **separate** `PENDING | APPROVED | DECLINED` tied to immutable estimate revision.

## Acceptance criteria
AC-01 Customer receives a case-scoped SMS link and sees current specific fee and versioned terms before accepting.
AC-02 Link opened without affirmative action remains PENDING.
AC-03 Unchecked box or missing consent statement causes server rejection.
AC-04 Accepted event captures exact snapshot, hash, timestamp, actor/method, case and consent version; immutable history survives later policy edits.
AC-05 Appointment alone never sets policy ACCEPTED; readiness requires both confirmed appointment and accepted policy.
AC-06 Changes to material fee/terms trigger re-acceptance; rescheduling alone retains acceptance when scope and fees are unchanged.
AC-07 Diagnostic authorization never permits repair without estimate-specific approval.
AC-08 Expired/used/guessed token is rejected; duplicate submit is idempotent; access control prevents cross-case disclosure.
AC-09 Desktop and mobile display the same consent status, receipt and audit trail; SMS failure remains visible.
AC-10 Invoice preview is watermarked and tax disclosure matches actual business tax status.
AC-11 Phone/staff-attested agreement is visibly labeled as such, with date/time, staff member and communication channel; not presented as customer click.
AC-12 System stores explicit timezone and visit window; audit timestamps UTC.

## Suggested customer-facing copy (subject to actual fee, terms and legal review)
**Diagnostic Visit Authorization** — I have reviewed the diagnostic visit fee of **[AMOUNT]**, the circumstances in which it is payable, and the applicable service terms. By selecting **I Agree & Confirm**, I agree to the disclosed diagnostic visit terms and authorize the described diagnostic service for my appointment. Any additional repair work requires my separate approval of a repair estimate.

☐ I have read and agree to the diagnostic visit fee and service terms.

**Button:** I Agree & Confirm
