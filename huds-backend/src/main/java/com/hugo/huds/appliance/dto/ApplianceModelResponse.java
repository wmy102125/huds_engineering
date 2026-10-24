package com.hugo.huds.appliance.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 9:33 p.m.
 */
public record ApplianceModelResponse(
        UUID applianceModelId,
        String applianceType,
        String brand,
        String modelNo,
        Long rowVersion) {
}
