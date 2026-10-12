-- V003 CR-001: design draft; validate against V001/V002 schema before execution.
-- UUID references intentionally not bound to legacy PK types until Sprint 0 schema reconciliation.
CREATE TABLE service_policy_versions (
  id UUID PRIMARY KEY,
  version_label TEXT NOT NULL UNIQUE,
  policy_text TEXT NOT NULL,
  policy_sha256 CHAR(64) NOT NULL,
  consent_text TEXT NOT NULL,
  consent_sha256 CHAR(64) NOT NULL,
  effective_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE pre_service_agreements (
  id UUID PRIMARY KEY,
  case_ref TEXT NOT NULL,
  customer_ref TEXT NOT NULL,
  policy_version_id UUID NOT NULL REFERENCES service_policy_versions(id),
  displayed_policy_text TEXT NOT NULL,
  displayed_fee_snapshot JSONB NOT NULL,
  displayed_snapshot_sha256 CHAR(64) NOT NULL,
  acknowledgment_method TEXT NOT NULL CHECK (acknowledgment_method IN ('CUSTOMER_WEB','STAFF_PHONE_ATTESTED')),
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','SUPERSEDED')),
  accepted_at TIMESTAMPTZ,
  staff_actor_ref TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status <> 'ACCEPTED' OR accepted_at IS NOT NULL)
);
CREATE TABLE pre_service_events (
  id UUID PRIMARY KEY,
  agreement_id UUID NOT NULL REFERENCES pre_service_agreements(id),
  event_type TEXT NOT NULL,
  event_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_type TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key TEXT NOT NULL UNIQUE
);
CREATE TABLE visit_appointment_confirmations (
  id UUID PRIMARY KEY,
  case_ref TEXT NOT NULL,
  agreement_id UUID REFERENCES pre_service_agreements(id),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  timezone_name TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PROPOSED','CONFIRMED','RESCHEDULED','CANCELLED')),
  confirmation_method TEXT NOT NULL,
  confirmed_at TIMESTAMPTZ,
  recorded_by_ref TEXT,
  CHECK (ends_at > starts_at)
);
-- SMS tokens: store only hashed values; never store plaintext in this table.
CREATE TABLE pre_service_link_tokens (
  id UUID PRIMARY KEY,
  case_ref TEXT NOT NULL,
  token_sha256 CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
