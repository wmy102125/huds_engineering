package com.hugo.huds.appliance.dto;

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
        UUID schematicDocumentId,
        String schematicLocation,
        UUID serviceManualDocumentId,
        Long rowVersion) {
}
