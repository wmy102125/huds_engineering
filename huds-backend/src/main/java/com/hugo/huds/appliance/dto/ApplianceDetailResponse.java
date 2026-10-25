package com.hugo.huds.appliance.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 11:06 p.m.
 */
public record ApplianceDetailResponse(
        ApplianceModelResponse applianceModelResponse,
        UUID applianceId,
        UUID applianceModelId,
        String serialNo,
        Boolean serialVerified,
        String identityNotes,
        Long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        UserBriefResponse createdBy,
        UserBriefResponse updatedBy,
        List<ApplianceServiceAddressResponse> addressResponses
) {
}
