package com.hugo.huds.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-14 10:08 p.m.
 * @description
 * Returned after a successful login. Permissions let the client show or hide features;
 * the server still checks them on every request.
 */
public record LoginResponse(
        UUID userId,
        String userName,
        String email,
        String role,
        List<String> permissions) {
}
