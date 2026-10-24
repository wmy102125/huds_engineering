package com.hugo.huds.customer.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 5:50 p.m.
 */
public record CustomerDetailResponse(UUID customerId,
                                     String customerName,
                                     String email,
                                     String phone,
                                     String notes,
                                     long rowVersion,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     UserBriefResponse createdBy,
                                     UserBriefResponse updatedBy) {
}
