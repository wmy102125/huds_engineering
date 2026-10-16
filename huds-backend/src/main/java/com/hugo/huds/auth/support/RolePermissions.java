package com.hugo.huds.auth.support;

import java.util.List;
import java.util.Map;

/**
 * @author meiyan wang
 * @date 2026-10-15 12:07 p.m.
 */
public class RolePermissions {
    public static final String CASE_READ_OWN = "CASE_READ_OWN";
    public static final String CASE_READ_ALL = "CASE_READ_ALL";
    public static final String CASE_REVIEW   = "CASE_REVIEW";
    public static final String USER_MANAGE   = "USER_MANAGE";
    public static final String AUDIT_READ    = "AUDIT_READ";
    private static final Map<String, List<String>> BY_ROLE = Map.of(
            "ADMIN",List.of(CASE_READ_ALL,  USER_MANAGE, AUDIT_READ),
            "TECHNICIAN",List.of(CASE_READ_OWN),
            "REVIEWER",List.of(CASE_READ_ALL, CASE_REVIEW, AUDIT_READ)
    );

    public RolePermissions() {
    }

    public static List<String> getPermissionsByRole(String role) {
        return BY_ROLE.get(role);
    }
}
