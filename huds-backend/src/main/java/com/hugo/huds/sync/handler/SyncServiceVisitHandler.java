package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.visit.dto.ServiceVisitRequest;
import com.hugo.huds.visit.dto.VisitTransitionRequest;
import com.hugo.huds.visit.service.ServiceVisitService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-03 6:58 p.m.
 */
@Component
public class SyncServiceVisitHandler implements SyncHandler {

    private final ServiceVisitService serviceVisitService;
    private final SyncPayloadReader payloadReader;

    public SyncServiceVisitHandler(ServiceVisitService serviceVisitService, SyncPayloadReader payloadReader) {
        this.serviceVisitService = serviceVisitService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.SERVICE_VISIT;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID serviceVisitId = mutation.entityId();
        Instant clientRecordedAt = mutation.clientRecordedAt();
        return switch (mutation.operation()) {
            case CREATE -> serviceVisitService.add(
                    actorUserId,
                    serviceVisitId,
                    clientRecordedAt,
                    payloadReader.read(mutation, ServiceVisitRequest.class));
            case UPDATE -> serviceVisitService.update(
                    serviceVisitId,
                    payloadReader.read(mutation, ServiceVisitRequest.class));
            case TRANSITION -> serviceVisitService.transition(
                    actorUserId,
                    serviceVisitId, mutation.rowVersion(),
                    payloadReader.read(mutation, VisitTransitionRequest.class),
                    clientRecordedAt);
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for service visit: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return serviceVisitService.fetchChanges(since, sinceId, limit);
    }
}
