package com.hugo.huds.diagnosticcase.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-29 1:42 p.m.
 */
public record DiagnosticCaseDetailResponse(UUID diagnosticCaseId,
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
                                           Instant createdAt,
                                           Instant updatedAt,
                                           UserBriefResponse createdBy,
                                           UserBriefResponse updatedBy,
                                           long rowVersion) {
}
