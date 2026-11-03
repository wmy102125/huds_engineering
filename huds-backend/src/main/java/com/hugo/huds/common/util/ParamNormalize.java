package com.hugo.huds.common.util;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 10:16 a.m.
 */
public class ParamNormalize {
    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /** Trims the text; empty or blank text becomes null so the database holds either real text or nothing. */
    public static String blankToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
