package com.hugo.huds.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapped to the existing {@code audit_events} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table. Audit events are append-only: once written,
 * a row is never updated.</p>
 */
@Entity
@Table(name = "audit_events")
@Immutable
@Getter
@Setter
public class AuditEvent {

  // Audit events are always created on the server, so the id is generated here.
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "audit_event_id", nullable = false, updatable = false)
  private UUID auditEventId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @Column(name = "correlation_id")
  private String correlationId;

  /** A JSON object (the database requires an object, not an array or a scalar). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false)
  private String metadata = "{}";

  @CreationTimestamp
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  public AuditEvent() {
    // Required by JPA.
  }
}