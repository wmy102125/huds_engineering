package com.hugo.huds.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code sync_mutations} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "sync_mutations")
@Setter
@Getter
public class SyncMutation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "sync_mutation_id", nullable = false, updatable = false)
  private UUID syncMutationId;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "client_mutation_id", nullable = false)
  private UUID clientMutationId;

  @Column(name = "device_id", nullable = false)
  private String deviceId;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "operation", nullable = false)
  private String operation;

  @Column(name = "status", nullable = false)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload", nullable = false)
  private String responsePayload;

  @Column(name = "client_recorded_at", nullable = false)
  private Instant clientRecordedAt;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  public SyncMutation() {
    // Required by JPA.
  }



}
