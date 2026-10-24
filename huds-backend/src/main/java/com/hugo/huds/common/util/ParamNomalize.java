package com.hugo.huds.common.util;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 10:16 a.m.
 */
public class ParamNomalize {
    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
