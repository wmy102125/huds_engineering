-- 1) users add lockout and password change timestamp columns
ALTER TABLE users
    ADD COLUMN failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until timestamptz,
    ADD COLUMN password_changed_at timestamptz;
ALTER TABLE users ADD CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0);

-- 2) Spring Session JDBC's 2 tables
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

-- 3)index used to count the number of login failures by IP
CREATE INDEX idx_audit_events_login_failed_ip  ON audit_events ((metadata ->> 'ip'), occurred_at DESC) WHERE event_type = 'AUTH_LOGIN_FAILED';