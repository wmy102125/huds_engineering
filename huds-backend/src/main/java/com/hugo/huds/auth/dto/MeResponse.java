package com.hugo.huds.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-16 11:26 a.m.
 * @description Current user and session details returned by GET /api/v1/auth/me.
 * The client can use this to show the current user and session details in the UI.
 */
public record MeResponse (UUID userId,
                          String userName,
                          String email,
                          String role,
                          List<String> permissions,
                          UUID deviceId,
                          Instant sessionExpiresAt){
}
