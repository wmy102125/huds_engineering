-- CR-002 DESIGN DRAFT ONLY. NOT APPROVED FOR FLYWAY EXECUTION.
-- Review existing V001-V003 deployed state, backfill, ledger coexistence and concurrency before promoting.
-- PostgreSQL 17 dialect. Illustrative additive schema; triggers/permissions/transactions not supplied.

CREATE TABLE parts_orders (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 case_id uuid NOT NULL REFERENCES diagnostic_cases(id) ON DELETE RESTRICT,
 estimate_revision_id uuid NOT NULL REFERENCES estimate_revisions(id) ON DELETE RESTRICT,
 authorization_id uuid REFERENCES authorizations(id) ON DELETE RESTRICT,
 supplier_name text, status text NOT NULL DEFAULT 'DRAFT',
 deposit_required numeric(12,2) NOT NULL DEFAULT 0 CHECK(deposit_required>=0),
 deposit_policy_snapshot text NOT NULL DEFAULT '',
 expected_arrival_on date, created_at timestamptz NOT NULL DEFAULT now(),
 row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_parts_orders_case ON parts_orders(case_id);
CREATE TABLE parts_order_items (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), parts_order_id uuid NOT NULL REFERENCES parts_orders(id) ON DELETE RESTRICT,
 part_name text NOT NULL, part_number text, supplier_sku text,
 qty_ordered numeric(12,3) NOT NULL CHECK(qty_ordered>0),
 qty_received numeric(12,3) NOT NULL DEFAULT 0 CHECK(qty_received>=0),
 customer_unit_price numeric(12,2) NOT NULL CHECK(customer_unit_price>=0),
 supplier_unit_cost numeric(12,2), status text NOT NULL DEFAULT 'REQUESTED',
 CHECK(qty_received<=qty_ordered)
);
CREATE TABLE parts_order_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), parts_order_id uuid NOT NULL REFERENCES parts_orders(id) ON DELETE RESTRICT,
 event_type text NOT NULL, details jsonb NOT NULL DEFAULT '{}'::jsonb,
 occurred_at timestamptz NOT NULL DEFAULT now(), actor_user_id uuid REFERENCES sys_users(id) ON DELETE RESTRICT
);
CREATE TABLE financial_transactions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), case_id uuid NOT NULL REFERENCES diagnostic_cases(id) ON DELETE RESTRICT,
 transaction_kind text NOT NULL CHECK(transaction_kind IN ('PAYMENT','REFUND')),
 amount numeric(12,2) NOT NULL CHECK(amount>0), currency char(3) NOT NULL DEFAULT 'CAD',
 payment_method text NOT NULL, transaction_status text NOT NULL CHECK(transaction_status IN ('PENDING','SETTLED','FAILED','REVERSED')),
 category text CHECK(category IN ('DIAGNOSTIC','PARTS_DEPOSIT','LABOUR','OTHER')),
 original_payment_id uuid REFERENCES financial_transactions(id) ON DELETE RESTRICT,
 provider_event_key text UNIQUE, idempotency_key uuid UNIQUE,
 occurred_at timestamptz NOT NULL DEFAULT now(),
 CHECK((transaction_kind='REFUND' AND original_payment_id IS NOT NULL) OR transaction_kind='PAYMENT')
);
CREATE TABLE service_invoices (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), case_id uuid NOT NULL REFERENCES diagnostic_cases(id) ON DELETE RESTRICT,
 invoice_number text UNIQUE, status text NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','ISSUED','VOIDED','CREDITED')),
 currency char(3) NOT NULL DEFAULT 'CAD', subtotal numeric(12,2) NOT NULL DEFAULT 0,
 tax_amount numeric(12,2) NOT NULL DEFAULT 0, total_charges numeric(12,2) NOT NULL DEFAULT 0,
 document_snapshot jsonb, content_sha256 char(64), issued_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE service_invoice_items (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), invoice_id uuid NOT NULL REFERENCES service_invoices(id) ON DELETE RESTRICT,
 item_type text NOT NULL, description text NOT NULL, quantity numeric(12,3) NOT NULL CHECK(quantity>0),
 unit_price numeric(12,2) NOT NULL, line_total numeric(12,2) NOT NULL
);
CREATE TABLE financial_allocations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), transaction_id uuid NOT NULL REFERENCES financial_transactions(id) ON DELETE RESTRICT,
 invoice_id uuid REFERENCES service_invoices(id) ON DELETE RESTRICT,
 parts_order_id uuid REFERENCES parts_orders(id) ON DELETE RESTRICT,
 allocated_amount numeric(12,2) NOT NULL CHECK(allocated_amount>0),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK((invoice_id IS NOT NULL)::int + (parts_order_id IS NOT NULL)::int = 1)
);
CREATE TABLE payment_receipts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), transaction_id uuid NOT NULL UNIQUE REFERENCES financial_transactions(id) ON DELETE RESTRICT,
 receipt_number text NOT NULL UNIQUE, receipt_snapshot jsonb NOT NULL,
 issued_at timestamptz NOT NULL DEFAULT now()
);
-- Proposed: ALTER TABLE service_visits ADD COLUMN visit_number integer;
-- Backfill by case and stable chronology; then add UNIQUE(case_id, visit_number) and NOT NULL.
-- Required app/DB invariants NOT implemented here: server state transitions, immutable issued snapshots,
-- allocation totals <= settled net receipts, refund cap, approved estimate match, authorized purchasing.
