package com.hugo.huds.auth.dto;

/**
 * @author meiyan wang
 * @date 2026-10-16 11:22 a.m.
 * Declared by the client at login. It only selects the session timeout profile (ADR-013)
 * and is never used for authorization decisions.
 */
public enum ClientType {
    DESKTOP,
    MOBILE
}
