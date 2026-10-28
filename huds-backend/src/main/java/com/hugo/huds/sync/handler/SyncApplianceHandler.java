package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.dto.ApplianceRequest;
import com.hugo.huds.appliance.service.ApplianceService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies APPLIANCE mutations by calling {@link ApplianceService}.
 *
 * @author Meiyan Wang
 * @date 2026-10-24 8:43 p.m.
 */
@Component
public class SyncApplianceHandler implements SyncHandler {

    private final ApplianceService applianceService;
    private final SyncPayloadReader payloadReader;

    public SyncApplianceHandler(ApplianceService applianceService, SyncPayloadReader payloadReader) {
        this.applianceService = applianceService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.APPLIANCE;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID applianceId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> applianceService.add(applianceId,
                    payloadReader.read(mutation, ApplianceRequest.class)).rowVersion();
            case UPDATE -> applianceService.update(applianceId, payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, ApplianceRequest.class)).rowVersion();
            case ARCHIVE -> applianceService.archive(applianceId,
                    payloadReader.requireRowVersion(mutation)).rowVersion();
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for APPLIANCE: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return applianceService.fetchChanges(since, sinceId, limit);
    }
}