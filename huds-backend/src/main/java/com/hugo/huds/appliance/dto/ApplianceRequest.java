package com.hugo.huds.appliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 10:59 p.m.
 */
public record ApplianceRequest(
        @NotNull(message = "applianceModelId is required")
        UUID applianceModelId,
        String serialNo,
        String identityNotes,
        Boolean serialVerified) {
}
