package com.hugo.huds.sync.dto;

import com.hugo.huds.common.api.ApiErrorResponse;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-21 1:21 p.m.
 */
public record SyncPushResultResponse(UUID mutationId,
                                     SyncPushStatus status,
                                     boolean replayed,
                                     SyncEntityType entityType,
                                     UUID entityId,
                                     Long rowVersion,
                                     List<UUID> blockedBy,
                                     ApiErrorResponse error) {

    public static SyncPushResultResponse accepted(SyncPushMutationRequest request, Long version) {
        return new SyncPushResultResponse(request.mutationId(), SyncPushStatus.ACCEPTED, false, request.entityType(), request.entityId(), version, null, null);
    }

    public static SyncPushResultResponse rejected(SyncPushMutationRequest request, ApplicationException ex) {
        ApiErrorResponse error = new ApiErrorResponse(
                ex.getErrorCode().name(), ex.getMessage(), ex.isRetryable(), ex.getCurrentVersion(),
                MDC.get(CorrelationIdFilter.CORRELATION_ID), null, null);
        return new SyncPushResultResponse(request.mutationId(), SyncPushStatus.REJECTED, false,
                request.entityType(), request.entityId(), null, null, error);
    }

    public static SyncPushResultResponse blocked(SyncPushMutationRequest request, List<UUID> blockedBy) {
        return new SyncPushResultResponse(request.mutationId(), SyncPushStatus.BLOCKED, false,
                request.entityType(), request.entityId(), null, blockedBy, null);
    }

    /**
     *
     * @param request Front end request
     * @return Same mutationId reused for a different mutation: a client bug, report without being recorded.
     */
    public static SyncPushResultResponse mutationIdReused(SyncPushMutationRequest request) {
        ApiErrorResponse error = new ApiErrorResponse(ErrorCode.MUTATION_ID_REUSED.name(), "The mutation id has already used for different mutation.", false, null, MDC.get(CorrelationIdFilter.CORRELATION_ID), null, null);
        return new SyncPushResultResponse(request.mutationId(), SyncPushStatus.REJECTED, false,
                request.entityType(), request.entityId(), null, null, error);

    }

    /** Copy of a stored result, marked as a replay. */
    public  SyncPushResultResponse asReplay() {
        return new SyncPushResultResponse(mutationId, status, true, entityType, entityId,
                rowVersion, blockedBy, error);
    }
}
