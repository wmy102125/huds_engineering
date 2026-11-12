package com.hugo.huds.diagnosticcase.summary.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.diagnosticcase.summary.dto.CustomerDecision;
import com.hugo.huds.diagnosticcase.summary.dto.DiagnosticOutcome;
import com.hugo.huds.diagnosticcase.summary.dto.FailureCategory;
import com.hugo.huds.diagnosticcase.summary.dto.RepairStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code case_summary_records} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>Sections 6 and 7 of the diagnostic record: the technician's summary of a case. One row per case.</p>
 */
@Entity
@Table(name = "case_summary_records")
@Getter
@Setter
public class CaseSummaryRecord extends AuthoredAuditableEntity {

    /** Chosen by the caller (the client generates it offline), so it has no generator. */
    @Id
    @Column(name = "case_summary_record_id", nullable = false, updatable = false)
    private UUID caseSummaryRecordId;

    @Column(name = "diagnostic_case_id", nullable = false, updatable = false)
    private UUID diagnosticCaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnostic_outcome")
    private DiagnosticOutcome diagnosticOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "repair_status")
    private RepairStatus repairStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_decision")
    private CustomerDecision customerDecision;

    /** Subsystems involved, a JSON array of category codes as text; defaults to an empty array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_categories", nullable = false)
    private String failureCategories = "[]";

    /** The main one of the failure categories (the database checks that it is one of them). */
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_failure_category")
    private FailureCategory primaryFailureCategory;

    @Column(name = "data_tags")
    private String dataTags;

    @Column(name = "technician_summary")
    private String technicianSummary;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public CaseSummaryRecord() {
        // Required by JPA.
    }
}
