package com.hugo.huds.auth.dto;

import java.time.Instant;

/**
 * @author Meiyan Wang
 * @date 2026-10-16 1:32 p.m.
 * @description
 * One active session of a user. {@code sessionRef} is a one-way hash of the real session id, so an
 * administrator can revoke a session without ever seeing a usable session credential.
 */
public record SessionInfoResponse(String sessionRef,
                                  String deviceId,
                                  String deviceLabel,
                                  String clientType,
                                  String ip,
                                  String userAgent,
                                  Instant createdAt,
                                  Instant lastAccessedAt,
                                  Instant expiresAt) {
}
