package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.dto.CaseTransitionRequest;
import com.hugo.huds.diagnosticcase.dto.DiagnosticCaseRequest;
import com.hugo.huds.diagnosticcase.service.DiagnosticCaseService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-29 10:40 a.m.
 */
@Component
public class SyncDiagnosticCaseHndler implements SyncHandler {

    private final DiagnosticCaseService diagnosticCaseService;
    private final SyncPayloadReader payloadReader;

    public SyncDiagnosticCaseHndler(DiagnosticCaseService diagnosticCaseService, SyncPayloadReader payloadReader) {
        this.diagnosticCaseService = diagnosticCaseService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID diagnosticCaseId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> diagnosticCaseService.add(
                    actorUserId,
                    diagnosticCaseId,
                    payloadReader.read(mutation, DiagnosticCaseRequest.class),mutation.clientRecordedAt());
            case TRANSITION -> diagnosticCaseService.transition(
                    actorUserId,
                    diagnosticCaseId,
                    payloadReader.read(mutation, CaseTransitionRequest.class),mutation.clientRecordedAt());
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for Diagnostic case: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return diagnosticCaseService.fetchChanges(since,sinceId,limit);
    }
}
