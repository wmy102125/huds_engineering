package com.hugo.huds.visit.safetycheck.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA entity mapped to the existing {@code visit_safety_checks} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>Section 2 of the diagnostic form: one row per visit, written by the technician before the repair.
 * The primary key is the id of the visit. The three JSON fields are defined by the front end.</p>
 */
@Entity
@Table(name = "visit_safety_checks")
@Getter
@Setter
public class VisitSafetyCheck extends AuthoredAuditableEntity {

    /** The id of the visit. It is chosen by the caller, so it has no generator. */
    @Id
    @Column(name = "service_visit_id", nullable = false, updatable = false)
    private UUID serviceVisitId;

    /** SAFETY / ACCESS CHECKS. JSON object as text; Hibernate sends every column, so it defaults to an empty object. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_checks", nullable = false)
    private String safetyChecks = "{}";

    /** UTILITY BASELINE: supply type and voltages. JSON object as text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "utility_baseline", nullable = false)
    private String utilityBaseline = "{}";

    /** ENVIRONMENT BASELINE: water, gas and ambient notes. JSON object as text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "environment_baseline", nullable = false)
    private String environmentBaseline = "{}";

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public VisitSafetyCheck() {
        // Required by JPA.
    }
}
