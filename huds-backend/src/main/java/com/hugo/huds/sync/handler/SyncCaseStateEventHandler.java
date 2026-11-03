package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.service.ApplianceService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.service.CaseStateEventService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-29 2:11 p.m.
 */
@Component
public class SyncCaseStateEventHandler implements SyncHandler {
    private final CaseStateEventService caseStateEventService;

    public SyncCaseStateEventHandler(CaseStateEventService caseStateEventService) {
        this.caseStateEventService = caseStateEventService;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE_STATE_EVENT;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        // Events are written by the server only; the client can read them, never push them.
        throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Case state events cannot be pushed.");

    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return caseStateEventService.fetchChanges(since, sinceId, limit);
    }
}
