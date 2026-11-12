package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.visit.completion.dto.VisitCompletionRequest;
import com.hugo.huds.visit.completion.service.VisitCompletionService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the completion record of a visit (entityType VISIT_COMPLETION). The entityId of a mutation is the
 * id of the record. A record is never archived, so only CREATE and UPDATE are supported.
 */
@Component
public class SyncVisitCompletionHandler implements SyncHandler {

    private final VisitCompletionService visitCompletionService;
    private final SyncPayloadReader payloadReader;

    public SyncVisitCompletionHandler(VisitCompletionService visitCompletionService, SyncPayloadReader payloadReader) {
        this.visitCompletionService = visitCompletionService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.VISIT_COMPLETION;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        return switch (mutation.operation()) {
            case CREATE -> visitCompletionService.add(actorUserId, mutation.entityId(),
                    payloadReader.read(mutation, VisitCompletionRequest.class));
            case UPDATE -> visitCompletionService.update(actorUserId, mutation.entityId(),
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, VisitCompletionRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for VISIT_COMPLETION: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return visitCompletionService.fetchChanges(since, sinceId, limit);
    }
}
