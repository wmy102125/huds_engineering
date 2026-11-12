package com.hugo.huds.visit.diagnosticstep.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.visit.diagnosticstep.dto.PowerState;
import com.hugo.huds.visit.diagnosticstep.dto.StepResult;
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
 * JPA entity mapped to the existing {@code diagnostic_steps} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>Section 3 of the diagnostic form: one row per hypothesis tested during a visit. A visit has many of them,
 * numbered from 1 (unique per visit).</p>
 */
@Entity
@Table(name = "diagnostic_steps")
@Getter
@Setter
public class DiagnosticStep extends AuthoredAuditableEntity {

    /** Chosen by the caller (the client generates it offline), so it has no generator. */
    @Id
    @Column(name = "diagnostic_step_id", nullable = false, updatable = false)
    private UUID diagnosticStepId;

    @Column(name = "service_visit_id", nullable = false, updatable = false)
    private UUID serviceVisitId;

    @Column(name = "step_no", nullable = false, updatable = false)
    private Integer stepNo;

    /** HYPOTHESIS / POSSIBLE CAUSE. */
    @Column(name = "hypothesis", nullable = false)
    private String hypothesis;

    /** TEST / MEASUREMENT: component, terminal pin or mode. */
    @Column(name = "test_target")
    private String testTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "power_state")
    private PowerState powerState;

    /** EXPECTED value with its unit, kept as written. */
    @Column(name = "expected")
    private String expected;

    /** ACTUAL measured value with its unit, kept as written. */
    @Column(name = "actual")
    private String actual;

    /** Null means the step has not been judged yet. */
    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private StepResult result;

    /** DECISION / NEXT STEP. */
    @Column(name = "next_step")
    private String nextStep;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public DiagnosticStep() {
        // Required by JPA.
    }
}
