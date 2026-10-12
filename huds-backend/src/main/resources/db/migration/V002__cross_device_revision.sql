-- HADS v1.1: Cross-device optimistic concurrency metadata.
-- Apply AFTER V001; additive only. PostgreSQL 17+.
-- No device-specific business tables: same records serve Desktop and Mobile.
BEGIN;
ALTER TABLE diagnostic_cases ADD COLUMN IF NOT EXISTS record_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE service_visits ADD COLUMN IF NOT EXISTS record_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE appliances ADD COLUMN IF NOT EXISTS record_revision BIGINT NOT NULL DEFAULT 0;
-- Application MUST update with: UPDATE ... SET record_revision = record_revision + 1
-- WHERE id = :id AND record_revision = :expected_revision;
-- 0 updated rows -> 412 Precondition Failed / concurrency conflict.
COMMIT;
