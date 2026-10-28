package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.dto.ApplianceModelRequest;
import com.hugo.huds.appliance.service.ApplianceModelService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-24 8:43 p.m.
 */
@Component
public class SyncApplianceModelHandler implements SyncHandler {

    private final ApplianceModelService applianceModelService;
    private final SyncPayloadReader payloadReader;

    public SyncApplianceModelHandler(ApplianceModelService applianceModelService, SyncPayloadReader payloadReader) {
        this.applianceModelService = applianceModelService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.APPLIANCE_MODEL;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID applianceModelId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> applianceModelService.add(applianceModelId,
                    payloadReader.read(mutation, ApplianceModelRequest.class)).rowVersion();
            case UPDATE -> applianceModelService.update(applianceModelId, payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, ApplianceModelRequest.class)).rowVersion();
            case ARCHIVE -> applianceModelService.archive(applianceModelId,
                    payloadReader.requireRowVersion(mutation)).rowVersion();
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for APPLIANCE_MODEL: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return applianceModelService.fetchChanges(since, sinceId, limit);
    }
}
