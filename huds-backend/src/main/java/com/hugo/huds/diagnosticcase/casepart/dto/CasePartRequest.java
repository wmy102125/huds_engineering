package com.hugo.huds.diagnosticcase.casepart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The content of a CASE_PART CREATE or UPDATE mutation. The case of a part never changes after the part is created.
 */
public record CasePartRequest(
        @NotNull(message = "Case is required")
        UUID diagnosticCaseId,

        @NotBlank(message = "Part name is required")
        @Size(max = 200, message = "Part name is too long")
        String partName,

        @Size(max = 100, message = "Part number is too long")
        String oemPartNo,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity,

        @Size(max = 30, message = "Unit is too long")
        String unit,

        @Size(max = 200, message = "Supplier is too long")
        String supplier,

        @NotNull(message = "Supply status is required")
        PartsSupplyStatus partsSupplyStatus,

        LocalDate orderedOn,

        LocalDate expectedArrivalOn,

        LocalDate arrivedOn,

        @Min(value = 0, message = "Warranty cannot be negative")
        @Max(value = 120, message = "Warranty is too long")
        Integer warrantyMonths,

        UUID installedVisitId) {
}
