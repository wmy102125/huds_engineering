package com.hugo.huds.diagnosticcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Body of a CASE CREATE or UPDATE mutation.
 * Only what the client may decide is here. The case number, the address snapshot, the state,
 * the timestamps and the audit columns are set by the server.
 * On UPDATE only reportedSymptoms and errorCodes may change; the links must stay the same.
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
        String errorCodes,
        UUID relatedCaseId,
        WorkflowStatus toState
) {
}