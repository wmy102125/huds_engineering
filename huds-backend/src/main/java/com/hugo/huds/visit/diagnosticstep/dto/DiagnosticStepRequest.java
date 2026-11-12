package com.hugo.huds.visit.diagnosticstep.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of a DIAGNOSTIC_STEP CREATE or UPDATE mutation: one row of section 3 of the diagnostic form.
 *
 * <p>The visit and the step number are chosen on create and cannot change afterwards, so on UPDATE they must
 * match the stored row. Every other field is replaced as sent: an empty value clears it (for example,
 * an empty result means "not judged yet").</p>
 *
 * @author Meiyan Wang
 * @date 2026-11-03 6:57 p.m.
 */
public record DiagnosticStepRequest(
        @NotNull(message = "ServiceVisitId is required")
        UUID serviceVisitId,
        @NotNull(message = "StepNo is required")
        @Min(value = 1, message = "StepNo must be at least 1")
        Integer stepNo,
        /* HYPOTHESIS / POSSIBLE CAUSE. */
        @NotBlank(message = "Hypothesis is required")
        @Size(max = 1000, message = "Hypothesis is too long")
        String hypothesis,
        /* TEST / MEASUREMENT: component, terminal pin or mode. */
        @Size(max = 500, message = "TestTarget is too long")
        String testTarget,
        PowerState powerState,
        /* EXPECTED value with its unit, kept as written. */
        @Size(max = 200, message = "Expected is too long")
        String expected,
        /* ACTUAL measured value with its unit, kept as written. */
        @Size(max = 200, message = "Actual is too long")
        String actual,
        StepResult result,
        /* DECISION / NEXT STEP in the technician's own words. */
        @Size(max = 1000, message = "NextStep is too long")
        String nextStep
) {
}
