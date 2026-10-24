package com.hugo.huds.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-16 6:53 p.m.
 * @description CustomerRequest include customer's email and address information.
 */
public record CustomerRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 254)
        String email,
        @NotBlank(message = "Customer name is required")
        String customerName,
        String phone,
        String notes
) {
}
