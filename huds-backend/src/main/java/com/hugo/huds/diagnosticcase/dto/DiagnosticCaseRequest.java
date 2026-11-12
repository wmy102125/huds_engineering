package com.hugo.huds.diagnosticcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Body of a CASE CREATE or UPDATE mutation.
 * Only what the client may decide is here. The case number, the address snapshot, the state,
 * the timestamps and the audit columns are set by the server.
 * On UPDATE only reportedSymptoms, primarySymptom, secondarySymptoms and failureTiming may change; the links must stay the same.
 * Error codes are not part of the case: each one is its own CASE_ERROR_CODE record.
 *
 * @author Meiyan Wang
 * @date 2026-10-28 11:30 a.m.
 */
public record DiagnosticCaseRequest(
        @NotNull(message = "ApplianceId is required")
        UUID applianceId,
        @NotNull(message = "CustomerId is required")
        UUID customerId,
        @NotNull(message = "ServiceAddressId is required")
        UUID serviceAddressId,
        @NotBlank(message = "ReportedSymptoms is required")
        String reportedSymptoms,
        /* PRIMARY SYMPTOM of the form. Empty means "not set" on create and "unchanged" on update. */
        PrimarySymptom primarySymptom,
        /* SECONDARY SYMPTOM(S) of the form: a JSON array of codes. Empty means [] on create and "unchanged" on update. */
        JsonNode secondarySymptoms,
        /* FAILURE TIMING / TRIGGER of the form: a JSON object. Empty means {} on create and "unchanged" on update. */
        JsonNode failureTiming,
        UUID relatedCaseId,
        WorkflowStatus toState
) {
}