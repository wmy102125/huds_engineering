# CR-002 Acceptance tests — must pass before Phase 1 sign-off
| ID | Scenario | Expected |
|---|---|---|
| CR2-01 | Diagnose on Visit #1, schedule Visit #2 | Same case, distinct numbered visits, no evidence overwritten |
| CR2-02 | Approve estimate rev1, change price in rev2 | Rev1 remains auditable; new approval required |
| CR2-03 | Attempt parts purchase without approval | 422/blocked; no supplier order |
| CR2-04 | Approve quote with disclosed total and deposit | Approval binds exact hash, policy, amount |
| CR2-05 | Receive parts deposit | Receipt issued; balance reduced once |
| CR2-06 | Duplicate gateway webhook/payment retry | One financial transaction, one allocation |
| CR2-07 | Supplier backorder then receipt | Timeline retains both events, follow-up can be scheduled |
| CR2-08 | Install part Visit #2 and pass verification | Actual installed part linked to visit/action; invoice permitted |
| CR2-09 | Invoice $354, prior paid $234, today $120 | Balance $0, total charges $354 (no double deposit) |
| CR2-10 | Partial payment | Correct balance and multiple receipt records |
| CR2-11 | Customer cancels special order | Terms and actual refund rules applied; reason/event preserved |
| CR2-12 | Refund $145 | Refund references original payment; net receipts corrected |
| CR2-13 | Part arrives DOA, third visit | Case stays open; parts and evidence not lost |
| CR2-14 | Customer accepts diagnostic only, declines repair | Diagnostic charge remains; no repair authorization inferred |
| CR2-15 | Offline payment/quote issue attempt | Queued/draft only, never falsely marked settled/issued |
| CR2-16 | Non-tax-registered business | No invented HST amount or registration number |
| CR2-17 | Reprint final invoice | Same issued snapshot; corrections via credit/reissue |
| CR2-18 | Unauthorized user attempts refund | Forbidden and audited |
| CR2-19 | Second visit fee absent from approved quote | No automatic new fee charged |
| CR2-20 | Desktop create, mobile follow-up, desktop settlement | Full cross-device flow, conflict handling and history |
