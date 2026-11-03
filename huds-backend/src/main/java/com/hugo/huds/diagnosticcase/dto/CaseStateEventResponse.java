package com.hugo.huds.diagnosticcase.dto;


import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * One step in the history of a case as GET /api/v1/sync/pull returns it (entityType CASE_STATE_EVENT).
 * The events are only ever added, never changed, so there is no row version, update time or archived flag.
 * The pull cursor of this type is occurredAt, not updatedAt.
 *
 * @author Meiyan Wang
 */
public record CaseStateEventResponse(
        UUID caseStateEventId,
        UUID diagnosticCaseId,
        WorkflowStatus fromState,
        WorkflowStatus toState,
        String reason,
        UserBriefResponse actor,
        Instant occurredAt,
        Instant clientRecordedAt
) {
}