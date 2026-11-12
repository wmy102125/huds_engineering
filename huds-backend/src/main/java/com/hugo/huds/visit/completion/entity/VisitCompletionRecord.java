package com.hugo.huds.visit.completion.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.visit.completion.dto.FinalStatus;
import com.hugo.huds.visit.completion.dto.FixOutcome;
import com.hugo.huds.visit.completion.dto.GoogleReviewStatus;
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
 * JPA entity mapped to the existing {@code visit_completion_records} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>Section 5 of the diagnostic form: one row per visit, written by the technician when the visit is finished.</p>
 */
@Entity
@Table(name = "visit_completion_records")
@Getter
@Setter
public class VisitCompletionRecord extends AuthoredAuditableEntity {

    /** Chosen by the caller (the client generates it offline), so it has no generator. */
    @Id
    @Column(name = "visit_completion_record_id", nullable = false, updatable = false)
    private UUID visitCompletionRecordId;

    @Column(name = "service_visit_id", nullable = false, updatable = false)
    private UUID serviceVisitId;

    /** VERIFICATION READINGS / TEST CYCLE / SAFETY CHECK. JSON object as text; defaults to an empty object. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_checks", nullable = false)
    private String verificationChecks = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status")
    private FinalStatus finalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "fix_outcome")
    private FixOutcome fixOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "google_review_status")
    private GoogleReviewStatus googleReviewStatus;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public VisitCompletionRecord() {
        // Required by JPA.
    }
}
