package com.hugo.huds.visit.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.visit.dto.VisitKind;
import com.hugo.huds.visit.dto.VisitStatus;
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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code service_visits} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "service_visits")
@Setter
@Getter
public class ServiceVisit extends AuthoredAuditableEntity {

  @Id
  @Column(name = "service_visit_id", nullable = false, updatable = false)
  private UUID serviceVisitId;

  /** A case can have many visits. */
  @Column(name = "diagnostic_case_id", nullable = false)
  private UUID diagnosticCaseId;

  @Column(name = "technician_id", nullable = false)
  private UUID technicianId;

  @Column(name = "visit_status", nullable = false)
  @Enumerated(EnumType.STRING)
  private VisitStatus visitStatus;

  /** First visit, later visit or recall. Sent on every insert, so it defaults to FIRST. */
  @Column(name = "visit_kind", nullable = false)
  @Enumerated(EnumType.STRING)
  private VisitKind visitKind = VisitKind.FIRST;

  /** When the visit is planned for. Only used while the visit is PLANNED. */
  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "notes")
  private String notes;

  @Version
  @Column(name = "row_version", nullable = false)
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  public ServiceVisit() {
    // Required by JPA.
  }
}