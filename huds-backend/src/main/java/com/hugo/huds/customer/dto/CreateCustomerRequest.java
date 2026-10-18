package com.hugo.huds.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author meiyan wang
 * @date 2026-10-16 6:53 p.m.
 * @description CustomerRequest include customer's email and address information.
 */
public record CreateCustomerRequest(@NotBlank(message = "Email is required")
                                    @Email(message = "Email format is invalid")
                                    @Size(max = 254)
                                    String email,
                                    @NotBlank(message = "customer name is required")
                                    String customerName,
                                    String phone,
                                    String notes,
                                    @NotBlank(message = "address line1 is required")
                                    String addressLine1,
                                    String addressLine2,
                                    @NotBlank(message = "city is required")
                                    String city,
                                    @NotBlank(message = "province is required")
                                    String province,
                                    @NotBlank(message = "postalCode is required")
                                    String postalCode,
                                    @NotBlank(message = "country code is required")
                                    String countryCode) {
}
