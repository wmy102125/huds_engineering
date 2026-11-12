package com.hugo.huds.diagnosticcase.summary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of a CASE_SUMMARY CREATE or UPDATE mutation: sections 6 and 7 of the diagnostic record.
 * The case of a summary never changes, so on UPDATE it must match the stored row.
 *
 * <p>The scalar fields are replaced as sent (empty means "not recorded"). An empty failure category list means
 * "no categories" on create and "unchanged" on update; send a non-empty list to change it. The primary category
 * must be one of the categories.</p>
 */
public record CaseSummaryRequest(
        @NotNull(message = "DiagnosticCaseId is required")
        UUID diagnosticCaseId,
        DiagnosticOutcome diagnosticOutcome,
        RepairStatus repairStatus,
        CustomerDecision customerDecision,
        /* Subsystems involved, for example [PMP, WIRE]. */
        @Size(max = 10, message = "FailureCategories has too many entries")
        List<FailureCategory> failureCategories,
        FailureCategory primaryFailureCategory,
        @Size(max = 500, message = "DataTags is too long")
        String dataTags,
        @Size(max = 5000, message = "TechnicianSummary is too long")
        String technicianSummary
) {
}
