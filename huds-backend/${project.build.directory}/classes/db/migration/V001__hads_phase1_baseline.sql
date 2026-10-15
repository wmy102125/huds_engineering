-- HADS Phase 1 baseline DDL | PostgreSQL 17+ | 2026-10-07
-- Review before production. Application must enforce state machines, authorization and evidence immutability.

--BEGIN;

CREATE TABLE users (
                       user_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_name text NOT NULL,
                       email text UNIQUE NOT NULL,
                       password_hash text NOT NULL,
                       role text NOT NULL CHECK (role IN ('TECHNICIAN', 'REVIEWER', 'ADMIN')),
                       active boolean NOT NULL DEFAULT true,
                       created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE customers (
                           customer_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                           customer_name text NOT NULL,
                           phone text,
                           email text,
                           notes text,
                           archived_at timestamptz,
                           created_at timestamptz NOT NULL DEFAULT now(),
                           row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE service_addresses (
                                   service_address_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                   customer_id uuid REFERENCES customers(customer_id) ON DELETE RESTRICT,
                                   address_line1 text NOT NULL,
                                   address_line2 text,
                                   city text NOT NULL,
                                   province text NOT NULL DEFAULT 'NS',
                                   postal_code text,
                                   country_code text NOT NULL DEFAULT 'CA',
                                   is_default boolean NOT NULL DEFAULT false,
                                   row_version bigint NOT NULL DEFAULT 0,
                                   archived_at timestamptz
);
CREATE INDEX idx_service_addresses_customer_id ON service_addresses(customer_id);

CREATE TABLE appliances (
                            appliance_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                            appliance_type text NOT NULL,
                            brand text,
                            model_no text,
                            serial_no text,
                            serial_verified boolean NOT NULL DEFAULT false,
                            identity_notes text,
                            created_at timestamptz NOT NULL DEFAULT now(),
                            archived_at timestamptz,
                            row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_appliance_identity ON appliances(brand, model_no, serial_no);

CREATE TABLE appliance_location_history (
                                            location_history_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                            appliance_id uuid NOT NULL REFERENCES appliances(appliance_id) ON DELETE RESTRICT,
                                            service_address_id uuid REFERENCES service_addresses(service_address_id) ON DELETE RESTRICT,
                                            customer_id uuid REFERENCES customers(customer_id) ON DELETE RESTRICT,
                                            valid_from timestamptz NOT NULL,
                                            valid_to timestamptz,
                                            note text,
                                            CONSTRAINT ck_appliance_location_history_valid_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX idx_appliance_location ON appliance_location_history(appliance_id, valid_from DESC);
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appliance_location_history ADD CONSTRAINT ex_appliance_location_no_overlap EXCLUDE USING gist (appliance_id WITH =, tstzrange(valid_from, valid_to, '[)') WITH &&);

CREATE TABLE diagnostic_cases (
                                  diagnostic_case_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  appliance_id uuid NOT NULL REFERENCES appliances(appliance_id) ON DELETE RESTRICT,
                                  customer_id uuid REFERENCES customers(customer_id) ON DELETE RESTRICT,
                                  service_address_id uuid REFERENCES service_addresses(service_address_id) ON DELETE RESTRICT,
                                  service_address_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
                                  reported_symptoms text NOT NULL,
                                  error_codes text,
                                  workflow_status text NOT NULL DEFAULT 'OPEN' CHECK (workflow_status IN ('OPEN', 'DIAGNOSING', 'PENDING_AUTHORIZATION', 'PENDING_PARTS', 'IN_REPAIR', 'VERIFYING', 'ON_HOLD', 'CLOSED', 'CANCELLED')),
                                  disposition text CHECK (disposition IN ('FIXED_VERIFIED', 'UNRESOLVED', 'CUSTOMER_DECLINED', 'BER', 'UNSAFE', 'NO_FAULT_FOUND', 'REFERRED', 'CANCELLED')),
                                  hold_reason text,
                                  related_case_id uuid REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                  opened_at timestamptz NOT NULL DEFAULT now(),
                                  closed_at timestamptz,
                                  created_by uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                                  row_version bigint NOT NULL DEFAULT 0,
                                  CONSTRAINT ck_diagnostic_cases_closed_at CHECK (workflow_status <> 'CLOSED' OR closed_at IS NOT NULL),
                                  CONSTRAINT ck_diagnostic_cases_disposition CHECK (workflow_status <> 'CLOSED' OR disposition IS NOT NULL)
);
CREATE INDEX idx_case_appliance ON diagnostic_cases(appliance_id, opened_at DESC);
CREATE INDEX idx_case_status ON diagnostic_cases(workflow_status, opened_at DESC);

CREATE TABLE case_state_events (
                                   case_state_event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                   diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                   from_state text CHECK (from_state IS NULL OR from_state IN ('OPEN', 'DIAGNOSING', 'PENDING_AUTHORIZATION', 'PENDING_PARTS', 'IN_REPAIR', 'VERIFYING', 'ON_HOLD', 'CLOSED', 'CANCELLED')),
                                   to_state text NOT NULL CHECK (to_state IN ('OPEN', 'DIAGNOSING', 'PENDING_AUTHORIZATION', 'PENDING_PARTS', 'IN_REPAIR', 'VERIFYING', 'ON_HOLD', 'CLOSED', 'CANCELLED')),
                                   reason text,
                                   actor_user_id uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                                   occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_case_state_events_case_time ON case_state_events(diagnostic_case_id, occurred_at DESC);

CREATE TABLE service_visits (
                                service_visit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                technician_id uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                                visit_status text NOT NULL DEFAULT 'PLANNED' CHECK (visit_status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED')),
                                started_at timestamptz,
                                ended_at timestamptz,
                                pause_reason text,
                                notes text,
                                row_version bigint NOT NULL DEFAULT 0,
                                CONSTRAINT ck_service_visits_time_order CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at),
                                CONSTRAINT ck_service_visits_completed_times CHECK (visit_status <> 'COMPLETED' OR (started_at IS NOT NULL AND ended_at IS NOT NULL))
);
CREATE INDEX idx_visits_case ON service_visits(diagnostic_case_id);

CREATE TABLE safety_assessments (
                                    safety_assessment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                    service_visit_id uuid NOT NULL REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,
                                    assessment_mode text NOT NULL CHECK (assessment_mode IN ('NOT_ASSESSED', 'CLEARED_STATIC', 'CLEARED_LIVE', 'STOP_WORK')),
                                    assessment_notes text,
                                    assessed_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                    assessed_at timestamptz NOT NULL DEFAULT now(),
                                    valid_until timestamptz,
                                    supersedes_id uuid REFERENCES safety_assessments(safety_assessment_id) ON DELETE RESTRICT,
                                    CONSTRAINT ck_safety_assessments_no_self_reference CHECK (supersedes_id IS NULL OR supersedes_id <> safety_assessment_id),
                                    CONSTRAINT ck_safety_assessments_valid_until CHECK (valid_until IS NULL OR valid_until > assessed_at)
);
CREATE INDEX idx_safety_assessments_visit_time ON safety_assessments(service_visit_id, assessed_at DESC);

CREATE TABLE safety_hazards (
                                safety_hazard_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                safety_assessment_id uuid NOT NULL REFERENCES safety_assessments(safety_assessment_id) ON DELETE RESTRICT,
                                hazard_code text NOT NULL,
                                risk_level text NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
                                control_measure text,
                                controlled boolean NOT NULL DEFAULT false
);
CREATE INDEX idx_safety_hazards_assessment ON safety_hazards(safety_assessment_id);

CREATE TABLE diagnostic_hypotheses (
                                       diagnostic_hypothesis_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                       diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                       description text NOT NULL,
                                       priority smallint NOT NULL CHECK (priority BETWEEN 1 AND 5),
                                       hypothesis_status text NOT NULL DEFAULT 'PROPOSED' CHECK (hypothesis_status IN ('PROPOSED', 'TESTING', 'SUPPORTED', 'REFUTED', 'INCONCLUSIVE')),
                                       created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                       created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_diagnostic_hypotheses_case ON diagnostic_hypotheses(diagnostic_case_id);

CREATE TABLE diagnostic_steps (
                                  diagnostic_step_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  diagnostic_hypothesis_id uuid NOT NULL REFERENCES diagnostic_hypotheses(diagnostic_hypothesis_id) ON DELETE RESTRICT,
                                  service_visit_id uuid REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,
                                  step_order integer NOT NULL DEFAULT 1 CHECK (step_order > 0),
                                  test_description text NOT NULL,
                                  test_point text,
                                  expected_result text,
                                  actual_observation text,
                                  interpretation text,
                                  next_step text,
                                  required_safety_mode text NOT NULL DEFAULT 'STATIC' CHECK (required_safety_mode IN ('STATIC', 'LIVE', 'NON_ELECTRICAL')),
                                  step_status text NOT NULL DEFAULT 'PLANNED' CHECK (step_status IN ('PLANNED', 'IN_PROGRESS', 'PASS', 'FAIL', 'INCONCLUSIVE', 'ABORTED')),
                                  performed_at timestamptz
);
CREATE INDEX idx_diagnostic_steps_hypothesis ON diagnostic_steps(diagnostic_hypothesis_id, step_order);

CREATE TABLE tool_assets (
                             tool_asset_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                             tool_name text NOT NULL,
                             make text,
                             model text,
                             serial_no text,
                             calibration_due date,
                             inspection_notes text,
                             active boolean NOT NULL DEFAULT true
);

CREATE TABLE measurement_records (
                                     measurement_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     diagnostic_step_id uuid NOT NULL REFERENCES diagnostic_steps(diagnostic_step_id) ON DELETE RESTRICT,
                                     tool_asset_id uuid REFERENCES tool_assets(tool_asset_id) ON DELETE RESTRICT,
                                     reading_kind text NOT NULL CHECK (reading_kind IN ('NUMERIC', 'OL', 'UNSTABLE', 'NOT_MEASURED', 'ABORTED')),
                                     actual_value numeric(18, 6),
                                     unit text,
                                     expected_value text,
                                     measurement_condition text,
                                     notes text,
                                     measured_at timestamptz NOT NULL DEFAULT now(),
                                     measured_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                     CONSTRAINT ck_measurement_records_value CHECK ((reading_kind = 'NUMERIC' AND actual_value IS NOT NULL) OR (reading_kind <> 'NUMERIC' AND actual_value IS NULL)),
                                     CONSTRAINT ck_measurement_records_unit CHECK (reading_kind <> 'NUMERIC' OR NULLIF(trim(unit), '') IS NOT NULL)
);
CREATE INDEX idx_measurement_records_step_time ON measurement_records(diagnostic_step_id, measured_at DESC);

CREATE TABLE evidence_amendments (
                                     evidence_amendment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     target_table text NOT NULL CHECK (target_table IN ('measurement_records', 'diagnostic_steps', 'root_cause_findings', 'repair_actions', 'repair_verifications')),
                                     target_id uuid NOT NULL,
                                     replacement_payload jsonb NOT NULL,
                                     reason text NOT NULL,
                                     amended_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                     amended_at timestamptz NOT NULL DEFAULT now(),
                                     CONSTRAINT ck_evidence_amendments_reason CHECK (length(trim(reason)) > 0)
);
CREATE INDEX idx_evidence_amendments_target ON evidence_amendments(target_table, target_id, amended_at DESC);

CREATE TABLE diagnostic_media (
                                  diagnostic_media_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                  diagnostic_step_id uuid REFERENCES diagnostic_steps(diagnostic_step_id) ON DELETE RESTRICT,
                                  media_kind text NOT NULL CHECK (media_kind IN ('PHOTO', 'VIDEO', 'AUDIO', 'DOCUMENT', 'SIGNATURE')),
                                  object_key text UNIQUE NOT NULL,
                                  mime_type text NOT NULL,
                                  byte_size bigint NOT NULL CHECK (byte_size >= 0),
                                  sha256_hex text NOT NULL CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    upload_status text NOT NULL DEFAULT 'PENDING' CHECK (upload_status IN ('PENDING', 'UPLOADING', 'UPLOADED', 'VERIFIED', 'FAILED')),
    captured_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT
);
CREATE INDEX idx_media_case ON diagnostic_media(diagnostic_case_id);
CREATE INDEX idx_media_step ON diagnostic_media(diagnostic_step_id);

CREATE TABLE root_cause_findings (
                                     root_cause_finding_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                     conclusion text NOT NULL,
                                     certainty text NOT NULL CHECK (certainty IN ('CONFIRMED', 'PROBABLE', 'UNDETERMINED')),
                                     supporting_evidence jsonb NOT NULL DEFAULT '[]'::jsonb,
                                     contradicting_evidence jsonb NOT NULL DEFAULT '[]'::jsonb,
                                     recorded_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                     row_version bigint NOT NULL DEFAULT 0,
                                     recorded_at timestamptz NOT NULL DEFAULT now(),
                                     CONSTRAINT ck_root_cause_findings_conclusion CHECK (length(trim(conclusion)) > 0)
);
CREATE INDEX idx_root_cause_findings_case ON root_cause_findings(diagnostic_case_id);

CREATE TABLE estimate_revisions (
                                    estimate_revision_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                    diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                    revision_no integer NOT NULL CHECK (revision_no > 0),
                                    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ISSUED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'SUPERSEDED')),
                                    currency char(3) NOT NULL DEFAULT 'CAD',
                                    total_amount numeric(12, 2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
                                    terms_snapshot text NOT NULL DEFAULT '',
                                    content_sha256 text CHECK (content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'),
    row_version bigint NOT NULL DEFAULT 0,
    issued_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_estimate_revisions_case_revision UNIQUE (diagnostic_case_id, revision_no),
    CONSTRAINT ck_estimate_revisions_issued_at CHECK (status NOT IN ('ISSUED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'SUPERSEDED') OR issued_at IS NOT NULL)
);
CREATE INDEX idx_estimate_revisions_case ON estimate_revisions(diagnostic_case_id);

CREATE TABLE estimate_items (
                                estimate_item_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                estimate_revision_id uuid NOT NULL REFERENCES estimate_revisions(estimate_revision_id) ON DELETE RESTRICT,
                                item_type text NOT NULL CHECK (item_type IN ('DIAGNOSTIC', 'LABOUR', 'PART', 'TRAVEL', 'DISPOSAL', 'DISCOUNT', 'TAX', 'OTHER')),
                                description text NOT NULL,
                                quantity numeric(12, 3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
                                unit_price numeric(12, 2) NOT NULL,
                                line_total numeric(12, 2) NOT NULL,
                                row_version bigint NOT NULL DEFAULT 0,
                                created_at timestamptz NOT NULL DEFAULT now(),
                                CONSTRAINT ck_estimate_items_description CHECK (length(trim(description)) > 0)
);
CREATE INDEX idx_estimate_items_revision ON estimate_items(estimate_revision_id);

CREATE TABLE authorizations (
                                authorization_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                estimate_revision_id uuid REFERENCES estimate_revisions(estimate_revision_id) ON DELETE RESTRICT,
                                authorization_stage text NOT NULL CHECK (authorization_stage IN ('DIAGNOSTIC_FEE', 'REPAIR_APPROVAL', 'COMPLETION_ACK')),
                                decision text NOT NULL CHECK (decision IN ('APPROVED', 'DECLINED', 'PENDING_REVIEW')),
                                consent_method text NOT NULL CHECK (consent_method IN ('SIGNATURE', 'VERBAL', 'EMAIL', 'TEXT', 'REFUSED_TO_SIGN', 'OTHER')),
                                scope_snapshot text NOT NULL CHECK (length(trim(scope_snapshot)) > 0),
                                scope_sha256 text CHECK (scope_sha256 IS NULL OR scope_sha256 ~ '^[0-9a-f]{64}$'),
    customer_name_snapshot text,
    diagnostic_media_id uuid REFERENCES diagnostic_media(diagnostic_media_id) ON DELETE RESTRICT,
    collected_offline boolean NOT NULL DEFAULT false,
    reviewed_at timestamptz,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    recorded_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_authorizations_repair_estimate CHECK (authorization_stage <> 'REPAIR_APPROVAL' OR estimate_revision_id IS NOT NULL),
    CONSTRAINT ck_authorizations_customer_name CHECK (customer_name_snapshot IS NULL OR length(trim(customer_name_snapshot)) > 0)
);
CREATE INDEX idx_authorizations_case_time ON authorizations(diagnostic_case_id, recorded_at DESC);
CREATE INDEX idx_authorizations_estimate_revision ON authorizations(estimate_revision_id);

CREATE TABLE repair_actions (
                                repair_action_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                service_visit_id uuid REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,
                                authorization_id uuid REFERENCES authorizations(authorization_id) ON DELETE RESTRICT,
                                action_type text NOT NULL CHECK (action_type IN ('CLEAN', 'ADJUST', 'REPAIR', 'REPLACE', 'REASSEMBLE', 'OTHER')),
                                component text,
                                description text NOT NULL,
                                action_status text NOT NULL DEFAULT 'PLANNED' CHECK (action_status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ABORTED')),
                                failure_reason text,
                                performed_by uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                                started_at timestamptz,
                                completed_at timestamptz,
                                row_version bigint NOT NULL DEFAULT 0,
                                CONSTRAINT ck_repair_actions_description CHECK (length(trim(description)) > 0),
                                CONSTRAINT ck_repair_actions_time_order CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at),
                                CONSTRAINT ck_repair_actions_completed_times CHECK (action_status <> 'COMPLETED' OR (started_at IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE INDEX idx_repair_actions_case ON repair_actions(diagnostic_case_id);

CREATE TABLE installed_parts (
                                 installed_part_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 repair_action_id uuid NOT NULL REFERENCES repair_actions(repair_action_id) ON DELETE RESTRICT,
                                 part_name text NOT NULL,
                                 part_number text,
                                 part_source text,
                                 serial_or_lot text,
                                 quantity numeric(12, 3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
                                 unit_cost numeric(12, 2) CHECK (unit_cost >= 0),
                                 condition text CHECK (condition IN ('NEW', 'REFURBISHED', 'USED', 'UNKNOWN')),
                                 installation_result text CHECK (installation_result IN ('INSTALLED', 'DOA', 'DAMAGED', 'REMOVED', 'RETURNED')),
                                 notes text,
                                 row_version bigint NOT NULL DEFAULT 0,
                                 CONSTRAINT ck_installed_parts_name CHECK (length(trim(part_name)) > 0)
);
CREATE INDEX idx_installed_parts_repair_action ON installed_parts(repair_action_id);

CREATE TABLE verification_templates (
                                        verification_template_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                        template_name text NOT NULL,
                                        appliance_type text NOT NULL,
                                        version_no integer NOT NULL DEFAULT 1 CHECK (version_no > 0),
                                        active boolean NOT NULL DEFAULT true,
                                        created_at timestamptz NOT NULL DEFAULT now(),
                                        CONSTRAINT uq_verification_templates_name_version UNIQUE (template_name, version_no),
                                        CONSTRAINT ck_verification_templates_name CHECK (length(trim(template_name)) > 0),
                                        CONSTRAINT ck_verification_templates_appliance_type CHECK (length(trim(appliance_type)) > 0)
);

CREATE TABLE repair_verifications (
                                      repair_verification_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                      service_visit_id uuid REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,
                                      verification_template_id uuid REFERENCES verification_templates(verification_template_id) ON DELETE RESTRICT,
                                      verification_round integer NOT NULL DEFAULT 1 CHECK (verification_round > 0),
                                      overall_result text NOT NULL CHECK (overall_result IN ('PENDING', 'PASS', 'FAIL', 'NA', 'NOT_COMPLETED')),
                                      notes text,
                                      performed_by uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                                      performed_at timestamptz,
                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      CONSTRAINT uq_repair_verifications_case_round UNIQUE (diagnostic_case_id, verification_round)
);

CREATE TABLE verification_checks (
                                     verification_check_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     repair_verification_id uuid NOT NULL REFERENCES repair_verifications(repair_verification_id) ON DELETE RESTRICT,
                                     check_name text NOT NULL,
                                     expected_result text,
                                     actual_result text,
                                     result text NOT NULL CHECK (result IN ('PASS', 'FAIL', 'NA', 'NOT_COMPLETED')),
                                     reason text,
                                     created_at timestamptz NOT NULL DEFAULT now(),
                                     CONSTRAINT ck_verification_checks_name CHECK (length(trim(check_name)) > 0),
                                     CONSTRAINT ck_verification_checks_reason CHECK (result NOT IN ('NA', 'NOT_COMPLETED') OR length(trim(reason)) > 0)
);
CREATE INDEX idx_verification_checks_verification ON verification_checks(repair_verification_id);

CREATE TABLE payment_records (
                                 payment_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                 amount numeric(12, 2) NOT NULL CHECK (amount >= 0),
                                 currency char(3) NOT NULL DEFAULT 'CAD',
                                 payment_method text NOT NULL CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'E_TRANSFER', 'CHEQUE', 'OTHER')),
                                 payment_status text NOT NULL CHECK (payment_status IN ('PENDING', 'RECEIVED', 'REFUNDED', 'VOID')),
                                 reference_no text,
                                 received_at timestamptz,
                                 created_at timestamptz NOT NULL DEFAULT now(),
                                 recorded_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                 CONSTRAINT ck_payment_records_received_at CHECK (payment_status NOT IN ('RECEIVED', 'REFUNDED') OR received_at IS NOT NULL)
);
CREATE INDEX idx_payment_records_case ON payment_records(diagnostic_case_id);

CREATE TABLE warranties (
                            warranty_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                            diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                            repair_action_id uuid REFERENCES repair_actions(repair_action_id) ON DELETE RESTRICT,
                            warranty_type text NOT NULL CHECK (warranty_type IN ('LABOUR', 'PART', 'SERVICE')),
                            coverage_snapshot text NOT NULL,
                            starts_on date NOT NULL,
                            ends_on date,
                            created_at timestamptz NOT NULL DEFAULT now(),
                            recorded_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                            CONSTRAINT ck_warranties_coverage CHECK (length(trim(coverage_snapshot)) > 0),
                            CONSTRAINT ck_warranties_date_range CHECK (ends_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX idx_warranties_case ON warranties(diagnostic_case_id);
CREATE INDEX idx_warranties_repair_action ON warranties(repair_action_id);

CREATE TABLE knowledge_candidates (
                                      knowledge_candidate_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      source_case_id uuid REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                      rule_key text NOT NULL,
                                      version_no integer NOT NULL CHECK (version_no > 0),
                                      title text NOT NULL,
                                      hypothesis_text text NOT NULL,
                                      review_status text NOT NULL DEFAULT 'CANDIDATE' CHECK (review_status IN ('CANDIDATE', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
                                      reviewed_by uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                                      reviewed_at timestamptz,
                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      recorded_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                      CONSTRAINT uq_knowledge_candidates_rule_version UNIQUE (rule_key, version_no),
                                      CONSTRAINT ck_knowledge_candidates_rule_key CHECK (length(trim(rule_key)) > 0),
                                      CONSTRAINT ck_knowledge_candidates_title CHECK (length(trim(title)) > 0),
                                      CONSTRAINT ck_knowledge_candidates_hypothesis CHECK (length(trim(hypothesis_text)) > 0),
                                      CONSTRAINT ck_knowledge_candidates_review CHECK (review_status NOT IN ('APPROVED', 'REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX idx_knowledge_candidates_source_case ON knowledge_candidates(source_case_id);
CREATE INDEX idx_knowledge_candidates_review_status ON knowledge_candidates(review_status);

CREATE TABLE knowledge_evidence (
                                    knowledge_evidence_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                    knowledge_candidate_id uuid NOT NULL REFERENCES knowledge_candidates(knowledge_candidate_id) ON DELETE RESTRICT,
                                    diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                    evidence_relation text NOT NULL CHECK (evidence_relation IN ('SUPPORTS', 'CONTRADICTS')),
                                    evidence_note text,
                                    created_at timestamptz NOT NULL DEFAULT now(),
                                    recorded_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                    CONSTRAINT uq_knowledge_evidence_candidate_case_relation UNIQUE (knowledge_candidate_id, diagnostic_case_id, evidence_relation),
                                    CONSTRAINT ck_knowledge_evidence_note CHECK (evidence_note IS NULL OR length(trim(evidence_note)) > 0)
);
CREATE INDEX idx_knowledge_evidence_candidate ON knowledge_evidence(knowledge_candidate_id);
CREATE INDEX idx_knowledge_evidence_case ON knowledge_evidence(diagnostic_case_id);

CREATE TABLE sync_mutations (
                                sync_mutation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                actor_user_id uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                client_mutation_id uuid NOT NULL,
                                device_id text NOT NULL,
                                entity_type text NOT NULL,
                                entity_id uuid NOT NULL,
                                operation text NOT NULL,
                                status text NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED')),
                                response_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
                                processed_at timestamptz NOT NULL DEFAULT now(),
                                CONSTRAINT uq_sync_mutations_actor_client_mutation UNIQUE (actor_user_id, client_mutation_id),
                                CONSTRAINT ck_sync_mutations_device_id CHECK (length(trim(device_id)) > 0),
                                CONSTRAINT ck_sync_mutations_entity_type CHECK (length(trim(entity_type)) > 0),
                                CONSTRAINT ck_sync_mutations_operation CHECK (length(trim(operation)) > 0),
                                CONSTRAINT ck_sync_mutations_response_payload CHECK (jsonb_typeof(response_payload) = 'object')
);
CREATE INDEX idx_sync_mutations_device_processed ON sync_mutations(device_id, processed_at DESC);
CREATE INDEX idx_sync_mutations_entity ON sync_mutations(entity_type, entity_id);

CREATE TABLE audit_events (
                              audit_event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                              actor_user_id uuid REFERENCES users(user_id) ON DELETE RESTRICT,
                              event_type text NOT NULL,
                              entity_type text NOT NULL,
                              entity_id uuid,
                              correlation_id text,
                              metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
                              occurred_at timestamptz NOT NULL DEFAULT now(),
                              CONSTRAINT ck_audit_events_event_type CHECK (length(trim(event_type)) > 0),
                              CONSTRAINT ck_audit_events_entity_type CHECK (length(trim(entity_type)) > 0),
                              CONSTRAINT ck_audit_events_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_correlation_id ON audit_events(correlation_id);

--COMMIT;