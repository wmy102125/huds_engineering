package com.hugo.huds.diagnosticcase.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.diagnosticcase.dto.ErrorCodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code case_error_codes} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>One row is one error code shown by the appliance (ERROR CODE / DISPLAY MESSAGE on the
 * diagnostic form). A case can have many of them.</p>
 */
@Entity
@Table(name = "case_error_codes")
@Getter
@Setter
public class CaseErrorCode extends AuthoredAuditableEntity {

    @Id
    @Column(name = "case_error_code_id", nullable = false, updatable = false)
    private UUID caseErrorCodeId;

    @Column(name = "diagnostic_case_id", nullable = false, updatable = false)
    private UUID diagnosticCaseId;

    /** The code as shown on the appliance, for example F21. */
    @Column(name = "error_code", nullable = false)
    private String errorCode;

    /** The message shown on the display, if any. */
    @Column(name = "display_message")
    private String displayMessage;

    /** Current fault or stored history. Sent on every insert, so it defaults to ACTIVE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "code_status", nullable = false)
    private ErrorCodeStatus codeStatus = ErrorCodeStatus.ACTIVE;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public CaseErrorCode() {
        // Required by JPA.
    }
}
