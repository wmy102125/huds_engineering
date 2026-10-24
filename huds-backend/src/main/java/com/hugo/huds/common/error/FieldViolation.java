package com.hugo.huds.common.error;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 11:51 a.m.
 * @description
 * A single field-level validation problem.
 * The rejected value is intentionally NOT included, to avoid leaking PII (email, phone, address) in responses and logs.
 */
public record FieldViolation(String field, String message) {
}
