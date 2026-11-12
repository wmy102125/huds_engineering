package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.summary.dto.CaseSummaryRequest;
import com.hugo.huds.diagnosticcase.summary.service.CaseSummaryService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the summary of a case (entityType CASE_SUMMARY). The entityId of a mutation is the id of the
 * summary. A summary is never archived, so only CREATE and UPDATE are supported.
 */
@Component
public class SyncCaseSummaryHandler implements SyncHandler {

    private final CaseSummaryService caseSummaryService;
    private final SyncPayloadReader payloadReader;

    public SyncCaseSummaryHandler(CaseSummaryService caseSummaryService, SyncPayloadReader payloadReader) {
        this.caseSummaryService = caseSummaryService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE_SUMMARY;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        return switch (mutation.operation()) {
            case CREATE -> caseSummaryService.add(mutation.entityId(),
                    payloadReader.read(mutation, CaseSummaryRequest.class));
            case UPDATE -> caseSummaryService.update(mutation.entityId(),
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, CaseSummaryRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for CASE_SUMMARY: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return caseSummaryService.fetchChanges(since, sinceId, limit);
    }
}
