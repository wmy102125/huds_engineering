# HADS Phase 1 Engineering Package v1.4 — Change Log
**Date:** 2026-10-08  **Status:** Design baseline; SQL/API extension requires review and implementation.

## CR-002 — Multi-Visit Repairs, Parts Deposits, and Final Settlement
- One service case supports multiple visits and rescheduling; each visit keeps separate work/safety/diagnostic/verification records.
- Customer approves a versioned estimate (total expected cost, labor, parts, applicable tax, lead time and deposit/cancellation policy) before a customer-funded parts order.
- Parts order, line items, supplier purchase, ETA, backorder, receipt, installation, return/cancellation and refund are distinct from installed parts.
- Deposits are **customer funds received**, not additional charges; preserve payment allocation and refund events. No duplicate charging on final invoice.
- Prepayment receipt and final invoice are separate artifacts. Final invoice displays prior payments/deposits, current payment and balance due.
- Customer authorization of diagnostic visit, parts purchase, and repair remain distinct; v1.3 CR-001 unchanged.
- Updated form **v1.1** is specified and a print-friendly DRAFT template supplied for review; original production v1.0 PDF remains unchanged.
- New **V004 design draft** is NOT an approved runnable Flyway migration. Freeze after implementation/review and promote under versioned migration policy.

## Compatibility
All v1.3 documents and historical SQL are preserved; v1.4 adds CR-002 design docs, a draft schema, API extension, QA cases and a printable draft form. Existing baseline fields are not silently repurposed.
