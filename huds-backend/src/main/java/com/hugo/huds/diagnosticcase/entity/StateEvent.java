package com.hugo.huds.diagnosticcase.entity;

import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code state_events} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "state_events")
@Getter
@Setter
public class StateEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "state_event_id")
  private UUID stateEventId;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  /** Null for the first event of an entity (it has no state before). */
  @Column(name = "from_state")
  private String fromState;

  @Column(name = "to_state", nullable = false)
  private String toState;

  @Column(name = "reason")
  private String reason;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  /** Server time when the event was recorded. It is the pull cursor. */
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  /** When the user did it on the (possibly offline) device. For display only. */
  @Column(name = "client_recorded_at")
  private Instant clientRecordedAt;
}
