-- HADS Phase 1 baseline DDL | PostgreSQL 17+
-- Single baseline migration. Run it on an EMPTY database.
-- Application must enforce state machines, authorization and evidence immutability.
--
-- Design of this version:
--   * an appliance belongs to one customer; a case links appliance, customer and service address (one case = one appliance).
--   * the diagnostic record form is stored per visit (sections 2 to 5) and per case (sections 1, 6 and 7).
--   * the content of the JSON fields is defined by the front end; the server only checks the JSON type and size.
--   * every synced table has updated_at / updated_by / row_version and an (updated_at, id) index for the incremental pull API.
--   * state_events is one history table for the state changes of every entity that has a status.
-- updated_at is maintained by the application (JPA auditing) and drives the pull cursor.
-- Not in Phase 1 (to be added by new migrations): authorizations, warranties, knowledge rules, repair verification templates.
-- From the first real deployment on, this file must never be edited: add new migrations instead.

--BEGIN;
-- Spring Session JDBC tables (session store, see ADR-013).
CREATE TABLE spring_session (
                                primary_id            char(36)     NOT NULL,
                                session_id            char(36)     NOT NULL,
                                creation_time         bigint       NOT NULL,
                                last_access_time      bigint       NOT NULL,
                                max_inactive_interval integer      NOT NULL,
                                expiry_time           bigint       NOT NULL,
                                principal_name        varchar(254),
                                CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
                                           session_primary_id char(36)     NOT NULL,
                                           attribute_name     varchar(200) NOT NULL,
                                           attribute_bytes    bytea        NOT NULL,
                                           CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
                                           CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
                                               REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

CREATE TABLE users (
                       user_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_name text NOT NULL,
                       email text UNIQUE NOT NULL,
                       password_hash text NOT NULL,
                       role text NOT NULL CHECK (role IN ('TECHNICIAN', 'REVIEWER', 'ADMIN')),
                       active boolean NOT NULL DEFAULT true,
                       failed_login_attempts integer NOT NULL DEFAULT 0,
                       locked_until timestamptz,
                       password_changed_at timestamptz,
                       created_at timestamptz NOT NULL DEFAULT now(),
                       updated_at timestamptz NOT NULL DEFAULT now(),
                       CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

CREATE TABLE customers (
                           customer_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                           customer_name text NOT NULL,

    -- Account on the form: who the customer is and who pays.
                           customer_type text NOT NULL DEFAULT 'RETAIL_COD'
                               CHECK (customer_type IN ('RETAIL_COD', 'PROPERTY_LANDLORD', 'THIRD_PARTY_WARRANTY', 'OEM_FACTORY')),

                           phone text,
                           email text,
                           notes text,
                           archived_at timestamptz,
                           created_at timestamptz NOT NULL DEFAULT now(),
                           updated_at timestamptz NOT NULL DEFAULT now(),
                           created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                           updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                           row_version bigint NOT NULL DEFAULT 0
);

-- One ACTIVE customer per email, ignoring case. Archived (soft-deleted) customers do not occupy
-- their email, so it can be reused. Customers without an email are not constrained.
CREATE UNIQUE INDEX ux_customers_email_lower
    ON customers (lower(email))
    WHERE email IS NOT NULL AND email <> '' AND archived_at IS NULL;
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_customers_changes ON customers (updated_at, customer_id);

CREATE TABLE service_addresses (
                                   service_address_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                   customer_id uuid NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
                                   address_line1 text NOT NULL,
                                   address_line2 text,
                                   city text NOT NULL,
                                   province text NOT NULL DEFAULT 'NS',
                                   postal_code text,
                                   country_code text NOT NULL DEFAULT 'CA',
                                   is_default boolean NOT NULL DEFAULT false,
                                   created_at timestamptz NOT NULL DEFAULT now(),
                                   updated_at timestamptz NOT NULL DEFAULT now(),
                                   created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                   updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                   row_version bigint NOT NULL DEFAULT 0,
                                   archived_at timestamptz
);
CREATE INDEX idx_service_addresses_customer_id ON service_addresses(customer_id);
-- At most one active default address per customer.
CREATE UNIQUE INDEX ux_service_addresses_one_default
    ON service_addresses (customer_id)
    WHERE is_default = true AND archived_at IS NULL;
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_service_addresses_changes ON service_addresses (updated_at, service_address_id);

-- Manuals and schematics. One row per document, reused by many appliance models.
-- The row can exist before the file does: the reference number is registered first and the file is uploaded later.
-- The file itself is in object storage.
CREATE TABLE appliance_documents (
                                     appliance_document_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     document_type         text NOT NULL CHECK (document_type IN ('SCHEMATIC', 'SERVICE_MANUAL', 'OTHER')),
    -- Number or version printed on the document (tech sheet number, manual publication number).
                                     document_ref          text NOT NULL,

    -- The file. All empty while upload_status is NO_FILE.
                                     object_key            text UNIQUE,
                                     file_name             text,
                                     mime_type             text,
                                     byte_size             bigint CHECK (byte_size >= 0),
                                     sha256_hex            text CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    upload_status         text NOT NULL DEFAULT 'NO_FILE'
                          CHECK (upload_status IN ('NO_FILE', 'PENDING', 'UPLOADED')),

    created_at            timestamptz NOT NULL DEFAULT now(),
    -- The upload status changes after the row is created, so the row needs these for sync.
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    updated_by            uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    row_version           bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_appliance_documents_ref CHECK (length(trim(document_ref)) > 0),
    -- NO_FILE means no object key; PENDING and UPLOADED mean there is one.
    CONSTRAINT ck_appliance_documents_key CHECK ((upload_status = 'NO_FILE') = (object_key IS NULL)),
    -- An uploaded file is fully described.
    CONSTRAINT ck_appliance_documents_uploaded CHECK (
        upload_status <> 'UPLOADED'
        OR (file_name IS NOT NULL AND mime_type IS NOT NULL AND byte_size IS NOT NULL AND sha256_hex IS NOT NULL))
);
-- The same document is registered once, so it can be reused by many appliances.
CREATE UNIQUE INDEX uq_appliance_documents_type_ref ON appliance_documents (document_type, lower(document_ref));
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_appliance_documents_changes ON appliance_documents (updated_at, appliance_document_id);


CREATE TABLE appliance_models (
                                  appliance_model_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  appliance_type text NOT NULL,
                                  brand text,
                                  model_no text,
    -- SCHEMATIC / TECH SHEET and SERVICE MANUAL references.
                                  schematic_document_id      uuid REFERENCES appliance_documents(appliance_document_id) ON DELETE RESTRICT,
                                  schematic_location         text,
                                  service_manual_document_id uuid REFERENCES appliance_documents(appliance_document_id) ON DELETE RESTRICT,

                                  created_at timestamptz NOT NULL DEFAULT now(),
                                  updated_at timestamptz NOT NULL DEFAULT now(),
                                  created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  archived_at timestamptz,
                                  row_version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_appliance_models_identity
    ON appliance_models (lower(appliance_type), coalesce(lower(brand), ''), coalesce(lower(model_no), ''))
    WHERE archived_at IS NULL;
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_appliance_models_changes ON appliance_models (updated_at, appliance_model_id);


CREATE TABLE appliances (
                            appliance_id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- The owner. Fixed when the appliance is created; one appliance belongs to one customer.
                            customer_id            uuid NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
                            appliance_model_id     uuid NOT NULL REFERENCES appliance_models(appliance_model_id) ON DELETE RESTRICT,
                            serial_no              text,
                            serial_verified        boolean NOT NULL DEFAULT false,
                            identity_notes         text,

    -- The customer's own asset or product number for this appliance (optional).
                            customer_asset_ref     text,
    -- P.O. # on the form: the purchase order number of this appliance.
                            purchase_order_number  text,
    -- MFG DATE / EST. AGE: whichever is known.
                            production_date        date,
                            estimated_age_years    integer CHECK (estimated_age_years >= 0),


                            created_at timestamptz NOT NULL DEFAULT now(),
                            updated_at timestamptz NOT NULL DEFAULT now(),
                            created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                            updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                            archived_at timestamptz,
                            row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_appliances_customer_id ON appliances(customer_id);
CREATE INDEX idx_appliance_identity ON appliances(appliance_model_id, serial_no);
-- The same serial number cannot exist twice for one model (active rows only).
CREATE UNIQUE INDEX uq_appliances_model_serial
    ON appliances (appliance_model_id, lower(serial_no))
    WHERE archived_at IS NULL AND serial_no IS NOT NULL AND serial_no <> '';
CREATE INDEX idx_appliances_changes ON appliances (updated_at, appliance_id);


-- Human-readable case numbers such as 2026-0042. The number is assigned by the server when the
-- case is accepted (offline devices show "pending" until the next pull), so numbers never clash.
-- One counter row per year; the counter is incremented inside the transaction that creates the case.
CREATE TABLE case_number_counters (
                                      case_year integer PRIMARY KEY,
                                      last_no integer NOT NULL CHECK (last_no >= 0)
);

CREATE TABLE diagnostic_cases (
                                  diagnostic_case_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  case_no text NOT NULL UNIQUE,
                                  appliance_id uuid NOT NULL REFERENCES appliances(appliance_id) ON DELETE RESTRICT,
                                  customer_id uuid NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
                                  service_address_id uuid NOT NULL REFERENCES service_addresses(service_address_id) ON DELETE RESTRICT,
                                  service_address_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,

    -- REPORTED SYMPTOM / CUSTOMER COMPLAINT (the customer's own words).
                                  reported_symptoms text NOT NULL,

    -- PRIMARY SYMPTOM, the root decision branch (one of the seven on the form).
                                  primary_symptom text CHECK (primary_symptom IN
                                                              ('NO_POWER', 'NO_START', 'NO_HEAT_COOL', 'LEAK_DRAIN', 'NOISE_VIBRATION', 'STOPS_MID_CYCLE', 'INTERMITTENT')),
    -- SECONDARY SYMPTOM(S), a list of codes: NOISE, ODOR, LEAK, SLOW_WEAK, OVERHEAT, TRIPS_BREAKER, OTHER.
                                  secondary_symptoms jsonb NOT NULL DEFAULT '[]'::jsonb
                    CHECK (jsonb_typeof(secondary_symptoms) = 'array' AND length(secondary_symptoms::text) <= 20000),
    -- FAILURE TIMING / TRIGGER CONDITION, an object such as {"type": "DURING_CYCLE", "minutes": 20, "phase": ""}.
                                  failure_timing jsonb NOT NULL DEFAULT '{}'::jsonb
                    CHECK (jsonb_typeof(failure_timing) = 'object' AND length(failure_timing::text) <= 20000),

                                  workflow_status text NOT NULL DEFAULT 'OPEN' CHECK (workflow_status IN ('OPEN', 'DIAGNOSING', 'PENDING_AUTHORIZATION', 'PENDING_PARTS', 'IN_REPAIR', 'ON_HOLD', 'CLOSED', 'CANCELLED')),
                                  disposition text CHECK (disposition IN ('FIXED_VERIFIED', 'UNRESOLVED', 'CUSTOMER_DECLINED', 'BER', 'UNSAFE', 'NO_FAULT_FOUND', 'REFERRED', 'CANCELLED')),
                                  hold_reason text,
                                  related_case_id uuid REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                  opened_at timestamptz NOT NULL DEFAULT now(),
                                  closed_at timestamptz,
                                  created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  created_at timestamptz NOT NULL DEFAULT now(),
                                  updated_at timestamptz NOT NULL DEFAULT now(),
                                  updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  row_version bigint NOT NULL DEFAULT 0,
    -- A case is never archived. It ends as CLOSED (with a disposition) or CANCELLED.
                                  CONSTRAINT ck_diagnostic_cases_closed_at CHECK (workflow_status NOT IN ('CLOSED', 'CANCELLED') OR closed_at IS NOT NULL),
                                  CONSTRAINT ck_diagnostic_cases_disposition CHECK (workflow_status <> 'CLOSED' OR (disposition IS NOT NULL AND disposition <> 'CANCELLED')),
                                  CONSTRAINT ck_diagnostic_cases_cancelled CHECK (workflow_status <> 'CANCELLED' OR disposition IS NOT DISTINCT FROM 'CANCELLED'),
    CONSTRAINT ck_diagnostic_cases_hold_reason CHECK (workflow_status <> 'ON_HOLD' OR length(trim(coalesce(hold_reason, ''))) > 0),
    CONSTRAINT ck_diagnostic_cases_symptoms CHECK (length(trim(reported_symptoms)) > 0)
);
-- An appliance can have only one case that is not finished (CLOSED / CANCELLED are the final states).
CREATE UNIQUE INDEX uq_case_one_unfinished_per_appliance
    ON diagnostic_cases (appliance_id)
    WHERE workflow_status NOT IN ('CLOSED', 'CANCELLED');
CREATE INDEX idx_case_appliance ON diagnostic_cases(appliance_id, opened_at DESC);
CREATE INDEX idx_case_customer ON diagnostic_cases(customer_id, opened_at DESC);
CREATE INDEX idx_case_service_address ON diagnostic_cases(service_address_id);
CREATE INDEX idx_case_status ON diagnostic_cases(workflow_status, opened_at DESC);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_cases_changes ON diagnostic_cases (updated_at, diagnostic_case_id);


-- ERROR CODE / DISPLAY MESSAGE: one row per code shown on the appliance.
CREATE TABLE case_error_codes (
                                  case_error_code_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                  error_code         text NOT NULL,
                                  display_message    text,
    -- ACTIVE: the current fault code. STORED_HISTORY: an earlier fault stored by the appliance.
                                  code_status        text NOT NULL DEFAULT 'ACTIVE' CHECK (code_status IN ('ACTIVE', 'STORED_HISTORY')),

                                  created_at timestamptz NOT NULL DEFAULT now(),
                                  updated_at timestamptz NOT NULL DEFAULT now(),
                                  created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  row_version bigint NOT NULL DEFAULT 0,

                                  CONSTRAINT ck_case_error_codes_code CHECK (length(trim(error_code)) > 0)
);
-- The same code is recorded once per case and status.
CREATE UNIQUE INDEX uq_case_error_codes_case_code
    ON case_error_codes (diagnostic_case_id, upper(error_code), code_status);
CREATE INDEX idx_case_error_codes_code ON case_error_codes (upper(error_code));
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_case_error_codes_changes ON case_error_codes (updated_at, case_error_code_id);


-- One append-only history table for the state changes of every entity that has a status
-- (case, visit, ...). entity_id points at the row of entity_type; there is no foreign key
-- because it can point at different tables, so the application checks it.
-- The allowed state values are checked by each entity's state machine in the application, not here.
CREATE TABLE state_events (
                              state_event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                              entity_type text NOT NULL CHECK (length(trim(entity_type)) > 0),
                              entity_id uuid NOT NULL,
                              from_state text,
                              to_state text NOT NULL CHECK (length(trim(to_state)) > 0),
                              reason text,
                              actor_user_id uuid REFERENCES users(user_id) ON DELETE RESTRICT,
    -- Server time when the event was recorded. It is the pull cursor, so it only ever moves forward.
                              occurred_at timestamptz NOT NULL DEFAULT now(),
    -- When the technician actually did it on the (possibly offline) device. For display only.
                              client_recorded_at timestamptz
);
CREATE INDEX idx_state_events_entity_time ON state_events(entity_type, entity_id, occurred_at DESC);
-- Incremental pull of the append-only events: ordered by (occurred_at, id).
CREATE INDEX idx_state_events_changes ON state_events (occurred_at, state_event_id);

CREATE TABLE service_visits (
                                service_visit_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                diagnostic_case_id uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                                technician_id      uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                visit_status       text NOT NULL DEFAULT 'PLANNED'
                                    CHECK (visit_status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED')),

    -- Visit on the form: the first visit of the case, a later visit (for example to fit ordered parts), or a recall.
                                visit_kind         text NOT NULL DEFAULT 'FIRST' CHECK (visit_kind IN ('FIRST', 'FOLLOW_UP', 'RECALL')),

    -- When the visit is planned for. Only used while the visit is PLANNED.
                                scheduled_at       timestamptz,
                                started_at         timestamptz,
                                ended_at           timestamptz,
    -- Free notes. The reason for a pause or a cancel is kept in the state_events row of that change.
                                notes              text,

                                created_at timestamptz NOT NULL DEFAULT now(),
                                updated_at timestamptz NOT NULL DEFAULT now(),
                                created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                row_version bigint NOT NULL DEFAULT 0,

                                CONSTRAINT ck_service_visits_time_order CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at),
                                CONSTRAINT ck_service_visits_completed_times CHECK (visit_status <> 'COMPLETED' OR (started_at IS NOT NULL AND ended_at IS NOT NULL)),
    -- A visit that has started (or finished) must have a start time.
                                CONSTRAINT ck_service_visits_started CHECK (visit_status NOT IN ('IN_PROGRESS', 'PAUSED', 'COMPLETED') OR started_at IS NOT NULL)
);
CREATE INDEX idx_visits_case ON service_visits(diagnostic_case_id);
-- A case can have only one visit that is on site (IN_PROGRESS or PAUSED) at a time.
CREATE UNIQUE INDEX uq_visit_one_open_per_case
    ON service_visits (diagnostic_case_id)
    WHERE visit_status IN ('IN_PROGRESS', 'PAUSED');
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_service_visits_changes ON service_visits (updated_at, service_visit_id);


-- Section 2 of the form: one row per visit, written by the technician before the repair.
-- The three JSON fields are defined by the front end (they depend on the appliance and the repair); the server only stores them.
CREATE TABLE visit_safety_checks (
                                     service_visit_id     uuid PRIMARY KEY REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,

    -- SAFETY / ACCESS CHECKS.
                                     safety_checks        jsonb NOT NULL DEFAULT '{}'::jsonb
                         CHECK (jsonb_typeof(safety_checks) = 'object' AND length(safety_checks::text) <= 20000),

    -- UTILITY BASELINE: supply type and voltages.
                                     utility_baseline     jsonb NOT NULL DEFAULT '{}'::jsonb
                         CHECK (jsonb_typeof(utility_baseline) = 'object' AND length(utility_baseline::text) <= 20000),

    -- ENVIRONMENT BASELINE: water, gas and ambient notes.
                                     environment_baseline jsonb NOT NULL DEFAULT '{}'::jsonb
                         CHECK (jsonb_typeof(environment_baseline) = 'object' AND length(environment_baseline::text) <= 20000),

                                     created_at timestamptz NOT NULL DEFAULT now(),
                                     updated_at timestamptz NOT NULL DEFAULT now(),
                                     created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                     updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                     row_version bigint NOT NULL DEFAULT 0
);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_visit_safety_checks_changes ON visit_safety_checks (updated_at, service_visit_id);

CREATE TABLE diagnostic_steps (
                                  diagnostic_step_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                  service_visit_id   uuid NOT NULL REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,
                                  step_no            integer NOT NULL CHECK (step_no > 0),

    -- HYPOTHESIS / POSSIBLE CAUSE
                                  hypothesis         text NOT NULL,

    -- TEST / MEASUREMENT: component, terminal pin or mode, and the power state during the test.
                                  test_target        text,
                                  power_state        text CHECK (power_state IN ('STATIC', 'LIVE')),

    -- EXPECTED (V / Ohm / A / Hz / deg F / psi) and ACTUAL (measured value + unit), kept as written.
                                  expected           text,
                                  actual             text,

    -- RESULT: P / F. NULL means the step has not been judged yet.
                                  result             text CHECK (result IN ('PASS', 'FAIL')),

    -- DECISION / NEXT STEP: written by the technician in their own words.
                                  next_step          text,

                                  created_at timestamptz NOT NULL DEFAULT now(),
                                  updated_at timestamptz NOT NULL DEFAULT now(),
                                  created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                  row_version bigint NOT NULL DEFAULT 0,

                                  CONSTRAINT ck_diagnostic_steps_hypothesis CHECK (length(trim(hypothesis)) > 0),
                                  CONSTRAINT uq_diagnostic_steps_visit_step UNIQUE (service_visit_id, step_no)
);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_diagnostic_steps_changes ON diagnostic_steps (updated_at, diagnostic_step_id);

-- Section 4 of the form: one row per visit, written by the technician during the repair.
CREATE TABLE visit_repair_records (
                                      visit_repair_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      service_visit_id       uuid NOT NULL UNIQUE REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,

    -- CONFIRMED ROOT CAUSE and CONFIDENCE.
                                      confirmed_root_cause text,
                                      confidence           text CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),

    -- FAILURE MODE: the primary mechanism.
                                      failure_mode         text CHECK (failure_mode IN
                                                                       ('OPEN', 'SHORT', 'STUCK', 'RESTRICTED', 'THERMAL', 'LOOSE_CORRODED', 'LOGIC_FIRMWARE', 'OTHER')),

    -- REPAIR ACTION / CORRECTIVE WORK PERFORMED
                                      repair_action        text,

                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      updated_at timestamptz NOT NULL DEFAULT now(),
                                      created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                      updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                      row_version bigint NOT NULL DEFAULT 0
);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_visit_repair_records_changes ON visit_repair_records (updated_at, visit_repair_record_id);


-- PARTS OF A CASE: the parts a repair needs, from the moment the case waits for them until they are used.
-- Replaces visit_repair_parts (which hung on a visit repair record and was never used by the code).
CREATE TABLE case_parts (
                            case_part_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                            diagnostic_case_id  uuid NOT NULL REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                            part_name           text NOT NULL,
                            oem_part_no         text,
                            quantity            numeric(12,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
                            unit                text,
                            supplier            text,

    -- SUPPLY STATUS of this part. ARRIVED: ordered or on backorder, and now in hand.
                            parts_supply_status text NOT NULL
                                CHECK (parts_supply_status IN ('IN_STOCK', 'ORDERED', 'BACKORDER', 'CUSTOMER_SUPPLIED', 'ARRIVED')),
                            ordered_on          date,
                            expected_arrival_on date,
                            arrived_on          date,

    -- WARRANTY of the part after it is installed, in months. Empty or 0 = no warranty.
                            warranty_months     integer CHECK (warranty_months >= 0),

    -- The visit in which the part was installed. Empty until the part is used.
                            installed_visit_id  uuid REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,

                            created_at timestamptz NOT NULL DEFAULT now(),
                            updated_at timestamptz NOT NULL DEFAULT now(),
                            created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                            updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                            row_version bigint NOT NULL DEFAULT 0,
    -- A part entered by mistake is archived, never deleted (the sync has to tell the other devices).
                            archived_at timestamptz,

                            CONSTRAINT ck_case_parts_name CHECK (length(trim(part_name)) > 0)
);
CREATE INDEX idx_case_parts_case ON case_parts (diagnostic_case_id);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_case_parts_changes ON case_parts (updated_at, case_part_id);


-- Section 5 of the form: one row per visit, written by the technician when the visit is finished.
CREATE TABLE visit_completion_records (
                                          visit_completion_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                          service_visit_id           uuid NOT NULL UNIQUE REFERENCES service_visits(service_visit_id) ON DELETE RESTRICT,

    -- VERIFICATION READINGS / TEST CYCLE / SAFETY CHECK. Check code -> true when checked.
    -- Defined by the front end (for example CURRENT_AMPS_IN_SPEC, RELEVANT_CYCLE_COMPLETED, NO_LEAK_VERIFIED, GROUND_SAFETY_CHECKED).
                                          verification_checks        jsonb NOT NULL DEFAULT '{}'::jsonb
                               CHECK (jsonb_typeof(verification_checks) = 'object' AND length(verification_checks::text) <= 20000),

    -- FINAL STATUS of the appliance when the technician leaves.
                                          final_status               text CHECK (final_status IN
                                                                                 ('OPERATING_NORMALLY', 'FURTHER_TESTING', 'PARTS_REQUIRED', 'NO_FAULT_FOUND', 'REPAIR_DECLINED', 'NOT_REPAIRABLE')),

    -- FIRST-TIME FIX / RECALL OUTCOME.
                                          fix_outcome                text CHECK (fix_outcome IN
                                                                                 ('FIRST_TIME_FIX', 'RETURN_VISIT_REQUIRED', 'RECALL_RESOLVED')),

    -- GOOGLE REVIEW STATUS. NULL means it has not been recorded.
                                          google_review_status       text CHECK (google_review_status IN ('NOT_REQUESTED', 'PENDING', 'REQUESTED', 'COMPLETED', 'DECLINED')),

                                          created_at timestamptz NOT NULL DEFAULT now(),
                                          updated_at timestamptz NOT NULL DEFAULT now(),
                                          created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                          updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                          row_version bigint NOT NULL DEFAULT 0
);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_visit_completion_records_changes ON visit_completion_records (updated_at, visit_completion_record_id);


-- Technician's summary of a case (sections 6 and 7 of the diagnostic record). One row per case.
CREATE TABLE case_summary_records (
                                      case_summary_record_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      diagnostic_case_id uuid NOT NULL UNIQUE REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,

    -- What the diagnosis found.
                                      diagnostic_outcome text CHECK (diagnostic_outcome IN ('FAULT_CONFIRMED', 'NO_TROUBLE_FOUND', 'BEYOND_ECONOMICAL_REPAIR')),
    -- How far the repair went.
                                      repair_status text CHECK (repair_status IN ('REPAIRED', 'PENDING_PARTS', 'INCOMPLETE')),
    -- What the customer decided.
                                      customer_decision text CHECK (customer_decision IN ('APPROVED', 'DECLINED', 'PENDING')),

    -- Subsystems involved, for example ["PMP", "WIRE"]. Allowed codes: PWR, CTRL, SENS, MTR, PMP, HEAT, REFRIG, WTR_DRN, MECH, WIRE.
                                      failure_categories jsonb NOT NULL DEFAULT '[]'::jsonb
                       CHECK (jsonb_typeof(failure_categories) = 'array' AND length(failure_categories::text) <= 20000),
    -- The main one of failure_categories. Must be one of the selected categories.
                                      primary_failure_category text,
                                      data_tags text,
                                      technician_summary text,

                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      updated_at timestamptz NOT NULL DEFAULT now(),
                                      created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                      updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                      row_version bigint NOT NULL DEFAULT 0,

                                      CONSTRAINT ck_case_summary_primary_category CHECK (
                                          primary_failure_category IS NULL OR failure_categories @> to_jsonb(primary_failure_category))
);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_case_summary_records_changes ON case_summary_records (updated_at, case_summary_record_id);



-- The bill of a case. One row per case; fees are added as the visits go on. Amounts are in CAD.
CREATE TABLE case_invoices (
                               case_invoice_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                               diagnostic_case_id uuid NOT NULL UNIQUE REFERENCES diagnostic_cases(diagnostic_case_id) ON DELETE RESTRICT,
                               invoice_status     text NOT NULL DEFAULT 'DRAFT' CHECK (invoice_status IN ('DRAFT', 'ISSUED', 'VOID')),
                               issued_at          timestamptz,

    -- Service Call & Diagnostic Fee: charged once per case, at the first visit.
                               service_call_fee   numeric(12,2) NOT NULL DEFAULT 0 CHECK (service_call_fee >= 0),
    -- Flat-Rate Labor / Service Performed.
                               labour_fee         numeric(12,2) NOT NULL DEFAULT 0 CHECK (labour_fee >= 0),
    -- Specialized Replacement Parts & Factory Materials.
                               parts_fee          numeric(12,2) NOT NULL DEFAULT 0 CHECK (parts_fee >= 0),
                               discount_amount    numeric(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    -- 0 while HST is not charged. For example 0.1500 once HST applies.
                               tax_rate           numeric(6,4) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0),

    -- Worked out by the database, so they can never disagree with the fees above.
                               subtotal_amount    numeric(12,2) GENERATED ALWAYS AS
                                   (service_call_fee + labour_fee + parts_fee) STORED,
                               tax_amount         numeric(12,2) GENERATED ALWAYS AS
                                   (round((service_call_fee + labour_fee + parts_fee - discount_amount) * tax_rate, 2)) STORED,
                               total_amount       numeric(12,2) GENERATED ALWAYS AS
                                   (service_call_fee + labour_fee + parts_fee - discount_amount
                                       + round((service_call_fee + labour_fee + parts_fee - discount_amount) * tax_rate, 2)) STORED,

                               created_at timestamptz NOT NULL DEFAULT now(),
                               updated_at timestamptz NOT NULL DEFAULT now(),
                               created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                               updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                               row_version bigint NOT NULL DEFAULT 0,

                               CONSTRAINT ck_case_invoices_discount CHECK (discount_amount <= service_call_fee + labour_fee + parts_fee),
                               CONSTRAINT ck_case_invoices_issued CHECK (invoice_status <> 'ISSUED' OR issued_at IS NOT NULL)
);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_case_invoices_changes ON case_invoices (updated_at, case_invoice_id);

-- Money received for a bill: one row per payment (the service call fee on the first visit, the rest later).
CREATE TABLE case_invoice_payments (
                                       case_invoice_payment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                       case_invoice_id         uuid NOT NULL REFERENCES case_invoices(case_invoice_id) ON DELETE RESTRICT,
                                       amount                  numeric(12,2) NOT NULL CHECK (amount > 0),
                                       payment_method          text NOT NULL CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'E_TRANSFER', 'CHEQUE')),
                                       reference_no            text,
                                       received_at             timestamptz NOT NULL,

                                       created_at timestamptz NOT NULL DEFAULT now(),
                                       updated_at timestamptz NOT NULL DEFAULT now(),
                                       created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                       updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                       row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_case_invoice_payments_invoice ON case_invoice_payments (case_invoice_id);
-- Incremental pull: ordered by (updated_at, id).
CREATE INDEX idx_case_invoice_payments_changes ON case_invoice_payments (updated_at, case_invoice_payment_id);




-- Corrections of saved evidence. The saved row is never overwritten silently: the new content and the reason are kept here.
CREATE TABLE evidence_amendments (
                                     evidence_amendment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     target_table text NOT NULL CHECK (target_table IN
                                                                       ('diagnostic_steps', 'visit_safety_checks', 'visit_repair_records', 'visit_repair_parts',
                                                                        'visit_completion_records', 'case_summary_records')),
                                     target_id uuid NOT NULL,
                                     replacement_payload jsonb NOT NULL,
                                     reason text NOT NULL,
                                     amended_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                                     amended_at timestamptz NOT NULL DEFAULT now(),
                                     CONSTRAINT ck_evidence_amendments_reason CHECK (length(trim(reason)) > 0)
);
CREATE INDEX idx_evidence_amendments_target ON evidence_amendments(target_table, target_id, amended_at DESC);

CREATE TABLE attachments (
                             attachment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- What the file is about.
    -- No foreign key because it points at different tables, so the application checks it (same as state_events).
                             entity_type         text NOT NULL CHECK (entity_type IN ('CASE', 'APPLIANCE', 'SERVICE_VISIT', 'DIAGNOSTIC_STEP')),
                             entity_id           uuid NOT NULL,

                             media_kind          text NOT NULL CHECK (media_kind IN ('PHOTO', 'VIDEO', 'AUDIO', 'DOCUMENT', 'SIGNATURE')),
                             object_key          text NOT NULL UNIQUE,
    -- Small preview of a photo or video, downloaded to the device ahead of time for offline use.
                             thumbnail_object_key text,
    -- Original file name, used as the download name.
                             file_name           text NOT NULL,
                             mime_type           text NOT NULL,
                             byte_size           bigint NOT NULL CHECK (byte_size >= 0),
                             sha256_hex          text NOT NULL CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    upload_status       text NOT NULL DEFAULT 'PENDING'
                        CHECK (upload_status IN ('PENDING', 'UPLOADING', 'UPLOADED', 'VERIFIED', 'FAILED')),
    captured_at         timestamptz,

    created_at timestamptz NOT NULL DEFAULT now(),
    -- The upload status changes after the row is created, so the row needs these for sync.
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    updated_by uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    row_version bigint NOT NULL DEFAULT 0
);

-- List all files of one case, visit, etc.
CREATE INDEX idx_attachments_entity
    ON attachments (entity_type, entity_id);

-- Pull changes for sync.
CREATE INDEX idx_attachments_changes
    ON attachments (updated_at, attachment_id);

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
    -- When the technician actually performed the action on the (offline) device.
    -- Untrusted device clock: informational / business display only, never used for ordering.
                                client_recorded_at timestamptz NOT NULL,
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
-- Used to count failed logins per IP (login rate limit).
CREATE INDEX idx_audit_events_login_failed_ip  ON audit_events ((metadata ->> 'ip'), occurred_at DESC) WHERE event_type = 'AUTH_LOGIN_FAILED';


--COMMIT;