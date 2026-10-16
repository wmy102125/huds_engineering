package com.hugo.huds.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-14 10:00 p.m.
 * @description
 * Request body for login. The password is never logged or audited.
 * @param email       account email
 * @param password    plain-text password; never logged or audited
 * @param deviceId    client-generated, persisted UUID; the same value the sync layer sends as deviceId
 * @param deviceLabel optional human-readable device name shown in the admin session list
 * @param clientType  DESKTOP (default) or MOBILE; selects the session timeout profile
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 200)
        String password,

        @NotNull(message = "deviceId is required")
        UUID deviceId,

        @Size(max = 100)
        String deviceLabel,

        ClientType clientType

) {
}
