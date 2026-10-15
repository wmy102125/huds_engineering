package com.hugo.huds.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * @author meiyan wang
 * @date 2026-10-14 11:21 p.m.
 */
public class BCryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("HudsAdminTest123!");
        System.out.println(hash);

    }
}
