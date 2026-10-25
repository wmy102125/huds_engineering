package com.hugo.huds.appliance.dto;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 11:06 p.m.
 */
public record ApplianceResponse(
        UUID applianceId,
        UUID applianceModelId,
        String serialNo,
        Boolean serialVerified,
        String identityNotes,
        Long rowVersion
) {
}
