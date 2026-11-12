package com.hugo.huds.visit.diagnosticstep.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * One diagnostic step as GET /api/v1/sync/pull returns it (entityType DIAGNOSTIC_STEP).
 * A step is never archived, so there is no "archived" flag.
 *
 * @author Meiyan Wang
 * @date 2026-11-03 6:57 p.m.
 */
public record DiagnosticStepDetailResponse(UUID diagnosticStepId,
                                           UUID serviceVisitId,
                                           Integer stepNo,
                                           String hypothesis,
                                           String testTarget,
                                           PowerState powerState,
                                           String expected,
                                           String actual,
                                           StepResult result,
                                           String nextStep,
                                           Instant createdAt,
                                           Instant updatedAt,
                                           UserBriefResponse creator,
                                           UserBriefResponse editor,
                                           Long rowVersion) {
}
