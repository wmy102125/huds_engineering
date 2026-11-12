package com.hugo.huds.diagnosticcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of a CASE_ERROR_CODE CREATE or UPDATE mutation: one ERROR CODE / DISPLAY MESSAGE line of the diagnostic form.
 * On UPDATE the case must stay the same.
 */
public record CaseErrorCodeRequest(
        @NotNull(message = "DiagnosticCaseId is required")
        UUID diagnosticCaseId,
        @NotBlank(message = "ErrorCode is required")
        @Size(max = 100, message = "ErrorCode is too long")
        String errorCode,
        @Size(max = 500, message = "DisplayMessage is too long")
        String displayMessage,
        /* Empty means ACTIVE on create and "unchanged" on update. */
        ErrorCodeStatus codeStatus
) {
}
