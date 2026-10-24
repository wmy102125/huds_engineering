package com.hugo.huds.address.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-19 6:06 p.m.
 */
public record ServiceAddressResponse(UUID serviceAddressId,
                                     UUID customerId,
                                     String addressLine1,
                                     String addressLine2,
                                     String city,
                                     String province,
                                     String postalCode,
                                     String countryCode,
                                     boolean isDefault,
                                     long rowVersion) {
}
