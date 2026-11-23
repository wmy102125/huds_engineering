package com.hugo.huds.sync.handler;

import com.hugo.huds.attachment.dto.AttachmentRequest;
import com.hugo.huds.attachment.dto.AttachmentResponse;
import com.hugo.huds.attachment.service.AttachmentService;
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
public class SyncAttachmentHandler implements SyncHandler {

    private final AttachmentService attachmentService;
    private final SyncPayloadReader syncPayloadReader;

    public SyncAttachmentHandler(AttachmentService attachmentService, SyncPayloadReader syncPayloadReader) {
        this.attachmentService = attachmentService;
        this.syncPayloadReader = syncPayloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.ATTACHMENT;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        if (mutation.operation() != SyncOperation.REGISTER) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for attachment: " + mutation.operation());
        }
        UUID attachmentId = mutation.entityId();
        AttachmentResponse response = attachmentService.register(attachmentId, syncPayloadReader.read(mutation, AttachmentRequest.class));
        return response.rowVersion();
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return attachmentService.fetchChanges(since, sinceId, limit);
    }
}
