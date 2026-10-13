package com.hugo.huds.common.api;

/**
 * @param code
 * @param message
 * @param retryable
 * @param currentVersion
 * @param correlationId
 *  api response example:
 *   {
 *    "code": "SAFETY_GATE_REQUIRED",
 *    "message": "Required safety assessment is missing.",
 *    "retryable": false,
 *    "currentVersion": null,
 *    "correlationId": "c4e92b1f-8b74-43e9-92ab-27e55adf64ae"
 *   }
 */
public record ApiErrorResponse(
        String code,
        String message,
        boolean retryable,
        Long currentVersion,
        String correlationId
) {
}