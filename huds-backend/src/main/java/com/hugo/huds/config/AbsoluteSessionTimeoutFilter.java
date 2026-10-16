package com.hugo.huds.config;

import com.hugo.huds.auth.support.AuthSessionAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author meiyan wang
 * @date 2026-10-16 10:12 a.m.
 * @description To reduce the risk of losing device state, enforces the absolute session lifetime.
 * spring Session only supports idle session timeout, which means that if the user is active, the session will not expire.
 * So the expiry instant is stored in the session at login and checked on every request. If the session is expired, it will be invalidated and the user will be logged out.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //false : never create a new session here.
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object expireAt = session.getAttribute(AuthSessionAttributes.ABSOLUTE_EXPIRES_AT);
            if (expireAt instanceof Long mills && System.currentTimeMillis() > mills) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
