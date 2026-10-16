package com.hugo.huds.auth.dto;

/**
 * @author meiyan wang
 * @date 2026-10-16 11:27 a.m.
 * @description Tells the client which header carries the CSRF token and what its value is.
 */
public record CsrfResponse(String headerName, String token) {
}
