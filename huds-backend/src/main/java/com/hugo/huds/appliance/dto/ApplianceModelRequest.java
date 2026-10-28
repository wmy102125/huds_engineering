package com.hugo.huds.appliance.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 9:33 p.m.
 */
public record ApplianceModelRequest(
        @NotBlank(message = "Appliance type is required")
        String applianceType,
        String brand,
        String modelNo) {
}
