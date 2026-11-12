package com.hugo.huds.visit.safetycheck.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * The safety and baseline checks of one visit as GET /api/v1/sync/pull returns them (entityType VISIT_SAFETY_CHECK).
 * The id of the record is the id of the visit. The record is never archived, so there is no "archived" flag.
 */
public record VisitSafetyCheckDetailResponse(UUID serviceVisitId,
                                             JsonNode safetyChecks,
                                             JsonNode utilityBaseline,
                                             JsonNode environmentBaseline,
                                             Instant createdAt,
                                             Instant updatedAt,
                                             UserBriefResponse creator,
                                             UserBriefResponse editor,
                                             Long rowVersion) {
}
