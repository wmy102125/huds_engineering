package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.visit.diagnosticstep.dto.DiagnosticStepRequest;
import com.hugo.huds.visit.diagnosticstep.service.DiagnosticStepService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the diagnostic steps of a visit (entityType DIAGNOSTIC_STEP). The entityId of a mutation is the
 * id of the step. A step is never archived, so only CREATE and UPDATE are supported.
 */
@Component
public class SyncDiagnosticStepHandler implements SyncHandler {

    private final DiagnosticStepService diagnosticStepService;
    private final SyncPayloadReader payloadReader;

    public SyncDiagnosticStepHandler(DiagnosticStepService diagnosticStepService, SyncPayloadReader payloadReader) {
        this.diagnosticStepService = diagnosticStepService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.DIAGNOSTIC_STEP;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        return switch (mutation.operation()) {
            case CREATE -> diagnosticStepService.add(actorUserId, mutation.entityId(),
                    payloadReader.read(mutation, DiagnosticStepRequest.class));
            case UPDATE -> diagnosticStepService.update(actorUserId, mutation.entityId(),
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, DiagnosticStepRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for DIAGNOSTIC_STEP: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return diagnosticStepService.fetchChanges(since, sinceId, limit);
    }
}
