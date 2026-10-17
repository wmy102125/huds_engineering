package com.hugo.huds.auth.support;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;

import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-16 5:27 p.m.
 * @description Resolves the id of the user who owns the current session.
 */

public class CurrentUser {
    private CurrentUser() {
    }

    public static UUID id(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(AuthSessionAttributes.USER_ID);
        if (value == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Not authenticated");
        }
        return UUID.fromString(value.toString());
    }
}
