package com.hugo.huds.appliance.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-24 6:06 p.m.
 */
public record ApplianceServiceAddressRequest(
        @NotNull(message = "ApplianceId is required")
        UUID applianceId,
        @NotNull(message = "ServiceAddressId is required")
        UUID serviceAddressId,
        String note,
        @NotNull(message = "RowVersion is required")
        Long rowVersion) {
}
