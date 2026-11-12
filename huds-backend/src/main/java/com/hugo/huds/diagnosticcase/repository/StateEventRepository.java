package com.hugo.huds.diagnosticcase.repository;

import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.entity.StateEvent;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StateEventRepository extends JpaRepository<StateEvent, UUID> {
    /**
     * The newest event of an entity that moved it INTO the given state.
     * Used to find the state before a hold: take this event's from_state.
     */
    Optional<StateEvent> findFirstByEntityTypeAndEntityIdAndToStateOrderByOccurredAtDescStateEventIdDesc(
            String entityType, UUID entityId, String toState);

    /**
     * Pull: events after the cursor (occurred_at, id), oldest first. The caller asks for limit + 1 rows to know if there are more.
     * Same style as the other pull queries: a native query with the user name joined in one go.
     */
    @Query(value = """
            select e.state_event_id as stateEventId,
                   e.entity_type    as entityType,
                   e.entity_id      as entityId,
                   e.from_state     as fromState,
                   e.to_state       as toState,
                   e.reason         as reason,
                   e.occurred_at    as occurredAt,
                   e.client_recorded_at as clientRecordedAt,
                   e.actor_user_id  as actorUserId,
                   u.user_name       as actorUserName
              from state_events e
              left join users u on u.user_id = e.actor_user_id
             where (e.occurred_at, e.state_event_id) > (:since, :sinceId)
             order by e.occurred_at, e.state_event_id
             limit :limit
            """, nativeQuery = true)
    java.util.List<ChangeRow> findChanges(@Param("since") Instant since,
                                          @Param("sinceId") UUID sinceId,
                                          @Param("limit") int limit);

    interface ChangeRow {
        UUID getStateEventId();

        String getEntityType();

        UUID getEntityId();

        String getFromState();

        String getToState();

        String getReason();

        Instant getOccurredAt();

        Instant getClientRecordedAt();

        UUID getActorUserId();

        String getActorUserName();

        /** The brief form of the actor, or null when the user could not be found. */
        default UserBriefResponse getActor() {
            return getActorUserId() == null || getActorUserName() == null
                    ? null
                    : new UserBriefResponse(getActorUserId(), getActorUserName());
        }
    }
}
