package com.hugo.huds.diagnosticcase.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.diagnosticcase.dto.CaseDisposition;
import com.hugo.huds.diagnosticcase.dto.PrimarySymptom;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code diagnostic_cases} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>A case is never archived, so there is no archived_at column and no soft-delete filter.</p>
 */
@Entity
@Table(name = "diagnostic_cases")
@Getter
@Setter
public class DiagnosticCase extends AuthoredAuditableEntity {

  @Id
  @Column(name = "diagnostic_case_id", nullable = false, updatable = false)
  private UUID diagnosticCaseId;

  /** Given by the server when the case is accepted (YYYY-NNNN). Never changes. */
  @Column(name = "case_no", nullable = false, unique = true, updatable = false)
  private String caseNo;

  @Column(name = "appliance_id", nullable = false, updatable = false)
  private UUID applianceId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "service_address_id", nullable = false, updatable = false)
  private UUID serviceAddressId;

  /** The address as it was when the case was opened, copied by the server. JSON text. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "service_address_snapshot", nullable = false, updatable = false)
  private String serviceAddressSnapshot;

  /** The customer's own words. */
  @Column(name = "reported_symptoms", nullable = false)
  private String reportedSymptoms;

  /** PRIMARY SYMPTOM on the form, the root decision branch. Filled by the technician. */
  @Enumerated(EnumType.STRING)
  @Column(name = "primary_symptom")
  private PrimarySymptom primarySymptom;

  /**
   * SECONDARY SYMPTOM(S) on the form: a JSON array of codes, for example {@code ["NOISE","ODOR"]}.
   * The codes are defined by the front end. JSON text. Sent on every insert, so it defaults to an empty array.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "secondary_symptoms", nullable = false)
  private String secondarySymptoms = "[]";

  /**
   * FAILURE TIMING / TRIGGER CONDITION on the form: a JSON object, for example
   * {@code {"type":"DURING_CYCLE","minutes":20}}. JSON text. Defaults to an empty object.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "failure_timing", nullable = false)
  private String failureTiming = "{}";

  @Enumerated(EnumType.STRING)
  @Column(name = "workflow_status", nullable = false)
  private WorkflowStatus workflowStatus;

  /** How the case ended. Empty while the case is active. */
  @Enumerated(EnumType.STRING)
  @Column(name = "disposition")
  private CaseDisposition disposition;

  @Column(name = "hold_reason")
  private String holdReason;

  @Column(name = "related_case_id", updatable = false)
  private UUID relatedCaseId;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  public DiagnosticCase() {
    // Required by JPA.
  }
}