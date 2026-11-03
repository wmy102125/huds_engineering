package com.hugo.huds.diagnosticcase.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One case as GET /api/v1/sync/pull returns it (entityType CASE).
 * A case is never archived, so unlike the other pulled records there is no "archived" flag.
 * Who created and last changed it is a small {userId, userName} object, or null if the user cannot be found.
 *
 * @param serviceAddressSnapshot the address as it was when the case was opened
 * @author Meiyan Wang
 */
public record DiagnosticCaseResponse(
        UUID diagnosticCaseId,
        String caseNo,
        UUID customerId,
        UUID serviceAddressId,
        UUID applianceId,
        Map<String, Object> serviceAddressSnapshot,
        String reportedSymptoms,
        String errorCodes,
        WorkflowStatus workflowStatus,
        CaseDisposition disposition,
        String holdReason,
        UUID relatedCaseId,
        Instant openedAt,
        Instant closedAt,
        long rowVersion
) {
}