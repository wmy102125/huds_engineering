package com.hugo.huds.user.dto;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-06 6:12 p.m.
 *
 * One record of the USER pull type: who a user is, for names and for choosing a technician. No email, no password.
 */
public record UserResponse(UUID userId, String userName, String role, boolean active) {
}
