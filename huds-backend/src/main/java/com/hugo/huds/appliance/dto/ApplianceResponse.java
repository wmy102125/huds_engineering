package com.hugo.huds.appliance.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 11:06 p.m.
 */
public record ApplianceResponse(
        UUID applianceId,
        UUID applianceModelId,
        UUID customerId,
        String serialNo,
        Boolean serialVerified,
        String identityNotes,
        String customerAssetRef,
        String purchaseOrderNumber,
        LocalDate productionDate,
        Integer estimatedAgeYears,
        Long rowVersion
) {
}
