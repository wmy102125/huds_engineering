package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.dto.ApplianceDocumentRequest;
import com.hugo.huds.appliance.dto.ApplianceDocumentResponse;
import com.hugo.huds.appliance.service.ApplianceDocumentService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dispatcher.SyncOperation;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-13 6:10 p.m.
 */
@Component
public class SyncApplianceDocumentHandler implements SyncHandler {

    private final ApplianceDocumentService applianceDocumentService;
    private final SyncPayloadReader syncPayloadReader;

    public SyncApplianceDocumentHandler(ApplianceDocumentService applianceDocumentService, SyncPayloadReader syncPayloadReader) {
        this.applianceDocumentService = applianceDocumentService;
        this.syncPayloadReader = syncPayloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.APPLIANCE_DOCUMENT;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        if (mutation.operation() != SyncOperation.REGISTER) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for appliance document: " + mutation.operation());
        }
        UUID applianceDocumentId = mutation.entityId();
        ApplianceDocumentResponse response = applianceDocumentService.register(applianceDocumentId, syncPayloadReader.read(mutation, ApplianceDocumentRequest.class));
        return response.rowVersion();
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return applianceDocumentService.fetchChanges(since, sinceId, limit);
    }
}
