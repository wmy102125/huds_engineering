package com.hugo.huds.user.reponse;

import java.util.UUID;

/**
 * A shorter reference to user, used wherever another record shows who created or updated it
 *
 * @author Meiyan Wang
 * @date 2026-10-23 11:24 a.m.
 */
public record UserBriefResponse(UUID userId, String userName) {
}
