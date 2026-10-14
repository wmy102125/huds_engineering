# CR-002 — Data dictionary and invariants
| Entity | Purpose | Relationship |
|---|---|---|
| `parts_orders` | Supplier/customer-authorized order, approval snapshot, deposit requirement and status | N:1 case; optional estimate revision and authorization |
| `parts_order_items` | Part number, qty, quoted customer price, purchase cost, supplier, ETA, received qty | N:1 parts order |
| `parts_order_events` | Immutable order status/receipt/cancel/backorder history | N:1 parts order |
| `financial_transactions` | Immutable payment/refund money movements, external idempotency | N:1 case |
| `financial_allocations` | Allocate funds to invoice or deposit/order, prevent double application | N:1 transaction |
| `service_invoices` | Immutable issued invoice totals and snapshot | N:1 case |
| `service_invoice_items` | Actual billable lines, never silently copy estimated values | N:1 invoice |
| `payment_receipts` | Receipt for money actually received | N:1 transaction |
| `visit_number` on `service_visits` | Human-visible chronological sequence, unique per case | additive column, backfill strategy required |

**Legacy reconciliation:** v1.3 `payment_records` already exists. Before implementing, decide whether to migrate into new ledger or retain it as read-only compatibility view. Do **not** write the same payment into both as independent truth. Choose one authoritative financial ledger and record migration mapping. `installed_parts` remains actual installation evidence; ordered/received items are distinct.

**Consistency:** Prevent negative settled receipt; refund references original receipt; transaction provider idempotency unique when present; issued invoice immutable; allocation must be transactional and cannot exceed available settled funds; customer-funded purchase needs approved quote revision and appropriate authorization. Reconcile supplier costs vs customer prices independently. Server enforces state transitions and authorization checks; CHECK constraints alone insufficient.

**Design migration:** `db/migrations/V004__parts_deposits_multi_visit_DESIGN_DRAFT.sql` is a *review artifact*, not deployable as-is; implement migration and tests after data/backfill decisions, then promote to an approved migration filename.
