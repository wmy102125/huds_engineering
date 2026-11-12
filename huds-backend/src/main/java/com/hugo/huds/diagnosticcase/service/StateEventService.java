package com.hugo.huds.diagnosticcase.service;

import com.hugo.huds.diagnosticcase.dto.StateEventEntityType;
import com.hugo.huds.diagnosticcase.dto.StateEventResponse;
import com.hugo.huds.diagnosticcase.entity.StateEvent;
import com.hugo.huds.diagnosticcase.repository.StateEventRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * @author Meiyan Wang
 * @date 2026-10-29 2:10 p.m.
 */
@Service
public class StateEventService {
    private final StateEventRepository stateEventRepository;

    public StateEventService(StateEventRepository stateEventRepository) {
        this.stateEventRepository = stateEventRepository;
    }
    /**
     * Records one state change. MANDATORY: it must run inside the transaction that changes the entity.
     *
     * @param fromState null for the first event of an entity
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(StateEventEntityType entityType, UUID entityId, String fromState, String toState,
                       String reason, UUID actorUserId, Instant clientRecordedAt) {
        StateEvent event = new StateEvent();
        event.setEntityType(entityType.name());
        event.setEntityId(entityId);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setReason(blankToNull(reason));
        event.setActorUserId(actorUserId);
        event.setOccurredAt(Instant.now());
        event.setClientRecordedAt(clientRecordedAt);
        stateEventRepository.save(event);
    }

    /** The state the case had before it was put on hold: the from_state of the newest event that moved it to ON_HOLD. */
    public String stateBeforeHold(StateEventEntityType entityType, UUID entityId, String holdState) {
        return stateEventRepository
                .findFirstByEntityTypeAndEntityIdAndToStateOrderByOccurredAtDescStateEventIdDesc(
                        entityType.name(), entityId, holdState)
                .map(StateEvent::getFromState)
                .orElse(null);
    }
    /**
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {
        List<StateEventRepository.ChangeRow> rows = stateEventRepository.findChanges(since, sinceId, limit + 1);
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }
        boolean hasMore = rows.size() > limit;
        List<StateEventRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<StateEventResponse> items = page.stream()
                .map(row -> new StateEventResponse(
                        row.getStateEventId(),
                        row.getEntityType(),
                        row.getEntityId(),
                        row.getFromState(),
                        row.getToState(),
                        row.getReason(),
                        row.getActor(),
                        row.getOccurredAt(),
                        row.getClientRecordedAt()))
                .toList();
        StateEventRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(items, last.getOccurredAt(), last.getStateEventId(), hasMore);
    }
}
