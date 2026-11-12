package com.hugo.huds.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hugo.huds.common.error.FieldViolation;

import java.util.List;
import java.util.UUID;

/**
 * @param code
 * @param message
 * @param retryable
 * @param currentVersion
 * @param correlationId  api response example:
 *                       {
 *                       "code": "VERSION_CONFLICT",
 *                       "message": "The case was changed by someone else.",
 *                       "retryable": false,
 *                       "currentVersion": 4,
 *                       "correlationId": "c4e92b1f-8b74-43e9-92ab-27e55adf64ae"
 *                       }
 */
public record ApiErrorResponse(
        String code,
        String message,
        boolean retryable,
        Long currentVersion,
        String correlationId,
        UUID conflictingResourceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldViolation> fieldViolations
) {
}