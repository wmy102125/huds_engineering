package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.visit.safetycheck.dto.VisitSafetyCheckRequest;
import com.hugo.huds.visit.safetycheck.service.VisitSafetyCheckService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the safety and baseline checks of a visit (entityType VISIT_SAFETY_CHECK).
 * The entityId of a mutation is the id of the visit.
 */
@Component
public class SyncVisitSafetyCheckHandler implements SyncHandler {

    private final VisitSafetyCheckService visitSafetyCheckService;
    private final SyncPayloadReader payloadReader;

    public SyncVisitSafetyCheckHandler(VisitSafetyCheckService visitSafetyCheckService, SyncPayloadReader payloadReader) {
        this.visitSafetyCheckService = visitSafetyCheckService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.VISIT_SAFETY_CHECK;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID serviceVisitId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> visitSafetyCheckService.add(actorUserId, serviceVisitId,
                    payloadReader.read(mutation, VisitSafetyCheckRequest.class));
            case UPDATE -> visitSafetyCheckService.update(actorUserId, serviceVisitId,
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, VisitSafetyCheckRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for VISIT_SAFETY_CHECK: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return visitSafetyCheckService.fetchChanges(since, sinceId, limit);
    }
}
