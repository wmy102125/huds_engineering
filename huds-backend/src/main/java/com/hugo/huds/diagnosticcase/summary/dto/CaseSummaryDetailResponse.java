package com.hugo.huds.diagnosticcase.summary.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * The summary of one case as GET /api/v1/sync/pull returns it (entityType CASE_SUMMARY).
 * A summary is never archived, so there is no "archived" flag.
 */
public record CaseSummaryDetailResponse(UUID caseSummaryRecordId,
                                        UUID diagnosticCaseId,
                                        DiagnosticOutcome diagnosticOutcome,
                                        RepairStatus repairStatus,
                                        CustomerDecision customerDecision,
                                        JsonNode failureCategories,
                                        FailureCategory primaryFailureCategory,
                                        String dataTags,
                                        String technicianSummary,
                                        Instant createdAt,
                                        Instant updatedAt,
                                        UserBriefResponse creator,
                                        UserBriefResponse editor,
                                        Long rowVersion) {
}
