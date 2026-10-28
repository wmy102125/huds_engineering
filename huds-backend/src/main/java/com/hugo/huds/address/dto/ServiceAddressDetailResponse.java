package com.hugo.huds.address.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 2:09 p.m.
 */
public record ServiceAddressDetailResponse(UUID serviceAddressId,
                                           UUID customerId,
                                           String addressLine1,
                                           String addressLine2,
                                           String city,
                                           String province,
                                           String postalCode,
                                           String countryCode,
                                           boolean isDefault,
                                           long rowVersion,
                                           Instant createdAt,
                                           Instant updatedAt,
                                           UserBriefResponse createdBy,
                                           UserBriefResponse updatedBy,
                                           boolean archived) {
}
