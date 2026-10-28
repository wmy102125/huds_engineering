package com.hugo.huds.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-19 4:36 p.m.
 */
public record ServiceAddressRequest(
        @NotNull(message = "Customer id is required")
        UUID customerId,
        @NotBlank(message = "Address line1 is required")
        String addressLine1,
        String addressLine2,
        @NotBlank(message = "City is required")
        String city,
        @NotBlank(message = "Province is required")
        String province,
        @NotBlank(message = "PostalCode is required")
        String postalCode,
        @NotBlank(message = "Country code is required")
        String countryCode,
        Boolean isDefault) {
}
