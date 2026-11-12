package com.hugo.huds.visit.completion.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * The completion record of one visit as GET /api/v1/sync/pull returns it (entityType VISIT_COMPLETION).
 * A record is never archived, so there is no "archived" flag.
 */
public record VisitCompletionDetailResponse(UUID visitCompletionRecordId,
                                            UUID serviceVisitId,
                                            JsonNode verificationChecks,
                                            FinalStatus finalStatus,
                                            FixOutcome fixOutcome,
                                            GoogleReviewStatus googleReviewStatus,
                                            Instant createdAt,
                                            Instant updatedAt,
                                            UserBriefResponse creator,
                                            UserBriefResponse editor,
                                            Long rowVersion) {
}
