package com.hugo.huds.diagnosticcase.casepart.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A part of a case as the pull sends it. Archived parts are included, with {@code archived} set,
 * so the other devices can remove them.
 */
public record CasePartDetailResponse(
        UUID casePartId,
        UUID diagnosticCaseId,
        String partName,
        String oemPartNo,
        BigDecimal quantity,
        String unit,
        String supplier,
        PartsSupplyStatus partsSupplyStatus,
        LocalDate orderedOn,
        LocalDate expectedArrivalOn,
        LocalDate arrivedOn,
        Integer warrantyMonths,
        UUID installedVisitId,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        UserBriefResponse createdBy,
        UserBriefResponse updatedBy,
        boolean archived) {
}
