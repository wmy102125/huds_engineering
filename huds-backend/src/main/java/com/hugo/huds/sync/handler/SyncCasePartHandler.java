package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.casepart.dto.CasePartRequest;
import com.hugo.huds.diagnosticcase.casepart.service.CasePartService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the parts of a case: CREATE, UPDATE and ARCHIVE.
 */
@Component
public class SyncCasePartHandler implements SyncHandler {

    private final CasePartService casePartService;
    private final SyncPayloadReader syncPayloadReader;

    public SyncCasePartHandler(CasePartService casePartService, SyncPayloadReader syncPayloadReader) {
        this.casePartService = casePartService;
        this.syncPayloadReader = syncPayloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE_PART;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID casePartId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> casePartService.add(casePartId, syncPayloadReader.read(mutation, CasePartRequest.class));
            case UPDATE -> casePartService.update(casePartId, syncPayloadReader.requireRowVersion(mutation),
                    syncPayloadReader.read(mutation, CasePartRequest.class));
            case ARCHIVE -> casePartService.archive(casePartId, syncPayloadReader.requireRowVersion(mutation));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for a part: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return casePartService.fetchChanges(since, sinceId, limit);
    }
}
