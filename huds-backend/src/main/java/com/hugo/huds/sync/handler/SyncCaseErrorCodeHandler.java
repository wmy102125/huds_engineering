package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.dto.CaseErrorCodeRequest;
import com.hugo.huds.diagnosticcase.service.CaseErrorCodeService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the error codes of a case (entityType CASE_ERROR_CODE).
 */
@Component
public class SyncCaseErrorCodeHandler implements SyncHandler {

    private final CaseErrorCodeService caseErrorCodeService;
    private final SyncPayloadReader payloadReader;

    public SyncCaseErrorCodeHandler(CaseErrorCodeService caseErrorCodeService, SyncPayloadReader payloadReader) {
        this.caseErrorCodeService = caseErrorCodeService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE_ERROR_CODE;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID caseErrorCodeId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> caseErrorCodeService.add(caseErrorCodeId,
                    payloadReader.read(mutation, CaseErrorCodeRequest.class));
            case UPDATE -> caseErrorCodeService.update(caseErrorCodeId, payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, CaseErrorCodeRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for CASE_ERROR_CODE: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return caseErrorCodeService.fetchChanges(since, sinceId, limit);
    }
}
