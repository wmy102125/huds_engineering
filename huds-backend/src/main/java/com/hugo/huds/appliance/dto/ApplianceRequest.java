package com.hugo.huds.appliance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 10:59 p.m.
 */
public record ApplianceRequest(
        @NotNull(message = "applianceModelId is required")
        UUID applianceModelId,
        @NotNull(message = "CustomerId is required")
        UUID customerId,
        String serialNo,
        String identityNotes,
        Boolean serialVerified,
        /* The customer's own asset or product number for this appliance. */
        String customerAssetRef,
        /* P.O. # on the form: the purchase order number of this appliance. */
        String purchaseOrderNumber,
        /* MFG DATE / EST. AGE on the form: whichever is known. */
        LocalDate productionDate,
        @Min(value = 0, message = "estimatedAgeYears cannot be negative")
        Integer estimatedAgeYears,
        /* Rows of appliance_documents. */
        UUID schematicDocumentId,
        String schematicLocation,
        UUID serviceManualDocumentId) {
}
