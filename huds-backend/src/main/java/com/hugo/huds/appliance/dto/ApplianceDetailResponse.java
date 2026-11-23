package com.hugo.huds.appliance.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 11:06 p.m.
 */
public record ApplianceDetailResponse(
        UUID applianceId,
        UUID customerId,
        UUID applianceModelId,
        String serialNo,
        boolean serialVerified,
        String identityNotes,
        String customerAssetRef,
        String purchaseOrderNumber,
        LocalDate productionDate,
        Integer estimatedAgeYears,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        UserBriefResponse createdBy,
        UserBriefResponse updatedBy,
        boolean archived
) {
}
