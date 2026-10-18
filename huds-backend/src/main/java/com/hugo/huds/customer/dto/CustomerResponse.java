package com.hugo.huds.customer.dto;

import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-18 5:50 p.m.
 */
public record CustomerResponse(UUID customerId,
                               String email,
                               String customerName,
                               String phone,
                               String notes,
                               String addressLine1,
                               String addressLine2,
                               String city,
                               String province,
                               String postalCode,
                               String countryCode) {
}
