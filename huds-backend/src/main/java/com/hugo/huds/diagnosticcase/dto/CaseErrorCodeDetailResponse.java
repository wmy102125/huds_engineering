package com.hugo.huds.diagnosticcase.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * One error code of a case as GET /api/v1/sync/pull returns it (entityType CASE_ERROR_CODE).
 * An error code is never archived, so there is no "archived" flag.
 */
public record CaseErrorCodeDetailResponse(UUID caseErrorCodeId,
                                          UUID diagnosticCaseId,
                                          String errorCode,
                                          String displayMessage,
                                          ErrorCodeStatus codeStatus,
                                          Instant createdAt,
                                          Instant updatedAt,
                                          UserBriefResponse creator,
                                          UserBriefResponse editor,
                                          Long rowVersion) {
}
