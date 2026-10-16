package com.hugo.huds.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author meiyan wang
 * @date 2026-10-16 1:34 p.m.
 * @description
 * New password chosen by an administrator. Minimum length 12, no complexity rules (ADR-013).
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 200, message = "Password must be 12 to 200 characters")
        String newPassword
) {
}
