package com.hugo.huds.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 1:55 p.m.
 */
public record UpdateCustomerRequest(UUID customerId,
                                    @NotBlank(message = "Email is required")
                                    @Email(message = "Email format is invalid")
                                    @Size(max = 254)
                                    String email,
                                    @NotBlank(message = "Customer name is required")
                                    String customerName,
                                    String phone,
                                    String notes,
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
                                    String countryCode) {
}
