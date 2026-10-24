package com.hugo.huds.customer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 5:50 p.m.
 */
public record CustomerResponse(UUID customerId,
                               String customerName,
                               String email,
                               String phone,
                               String notes,
                               long rowVersion) {
}
