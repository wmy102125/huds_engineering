package com.hugo.huds.auth.dto;

import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-14 10:08 p.m.
 */
public record LoginResponse(
        UUID userId,
        String userName,
        String email,
        String role) {
}
