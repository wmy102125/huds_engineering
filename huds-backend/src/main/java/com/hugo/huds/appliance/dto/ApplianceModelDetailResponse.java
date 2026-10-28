package com.hugo.huds.appliance.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 9:33 p.m.
 */
public record ApplianceModelDetailResponse(
        UUID applianceModelId,
        String applianceType,
        String brand,
        String modelNo,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        UserBriefResponse createdBy,
        UserBriefResponse updatedBy,
        boolean archived) {
}
