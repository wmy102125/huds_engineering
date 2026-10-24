package com.hugo.huds.auth.service;

import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.auth.dto.ClientType;
import com.hugo.huds.auth.dto.LoginRequest;
import com.hugo.huds.auth.dto.LoginResponse;
import com.hugo.huds.auth.dto.MeResponse;
import com.hugo.huds.auth.support.AuthSessionAttributes;
import com.hugo.huds.auth.support.ClientIp;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import com.hugo.huds.config.AuthProperties;
import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.repository.UsersRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * @author Meiyan Wang
 * @date 2026-10-14 10:43 p.m.
 * @description Session-based login and logout (ADR-013).
 *
 * <p>Every credential failure returns the same 401, so callers cannot tell "unknown user" from
 * "wrong password" or "disabled account". Account lockout and per-IP limiting return 429.
 * Outcomes are written to {@code audit_events}; passwords and session ids are never logged.</p>
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_FAILURE = "Invalid email or password.";

    private final AuthenticationManager authenticationManager;
    private final UsersRepository usersRepository;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final AuthProperties properties;

    public AuthService(AuthenticationManager authenticationManager, UsersRepository usersRepository, SecurityContextRepository securityContextRepository, LoginAttemptService loginAttemptService, AuditService auditService, AuthProperties properties) {
        this.authenticationManager = authenticationManager;
        this.usersRepository = usersRepository;
        this.securityContextRepository = securityContextRepository;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        String email = loginRequest.email().trim();
        String ip = ClientIp.resolve(request);
        String correlationId = correlationId(request);
        ClientType clientType = loginRequest.clientType() == null ? ClientType.DESKTOP : loginRequest.clientType();

        // Audit metadata. The attempted email and the password are deliberately never included.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ip", ip);
        metadata.put("deviceId", loginRequest.deviceId().toString());
        metadata.put("clientType", clientType.name());

        // 1) Per-IP limit, counted from audit_events so it survives restarts.
        long recentFailures = auditService.countRecentLoginFailuresByIp(ip, properties.ipFailureWindow());
        if (recentFailures >= properties.ipFailureLimit()) {
            Map<String, Object> blocked = new HashMap<>(metadata);
            blocked.put("reason", "IP_RATE_LIMIT");
            auditService.record(AuditService.AUTH_LOGIN_BLOCKED, "USER", null, null, correlationId, blocked);
            throw new ApplicationException(ErrorCode.RATE_LIMITED,
                    "Too many login attempts. Try again later.", true, null);

        }

        // 2) Temporary account lock.
        Optional<Users> existing = usersRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent() && isLocked(existing.get())) {
            Map<String, Object> blocked = new HashMap<>(metadata);
            blocked.put("reason", "ACCOUNT_LOCKED");
            UUID lockedUserId = existing.get().getUserId();
            auditService.record(AuditService.AUTH_LOGIN_BLOCKED, "USER",
                    lockedUserId, lockedUserId, correlationId, blocked);
            throw new ApplicationException(ErrorCode.RATE_LIMITED,
                    "Too many failed attempts. Try again later.", true, null);
        }

        // 3) Credentials.
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginRequest.password()));
        } catch (AuthenticationException ex) {
            handleFailure(existing, ex, metadata, correlationId);
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED, GENERIC_FAILURE);
        }
        Users user = usersRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be found"));

        loginAttemptService.registerSuccess(user.getUserId());

        // 4) A new session on every login prevents session fixation. Then store the security
        //    context in it, because Spring Security does not do that automatically for us.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);

        Duration idle = clientType == ClientType.MOBILE? properties.mobileIdleTimeout(): properties.desktopIdleTimeout();
        Duration absolute = clientType == ClientType.MOBILE? properties.mobileAbsoluteTimeout(): properties.desktopAbsoluteTimeout();

        session.setMaxInactiveInterval((int) idle.toSeconds());
        session.setAttribute(AuthSessionAttributes.USER_ID, user.getUserId().toString());
        session.setAttribute(AuthSessionAttributes.DEVICE_ID, loginRequest.deviceId().toString());
        session.setAttribute(AuthSessionAttributes.DEVICE_LABEL,
                loginRequest.deviceLabel() == null ? "" : loginRequest.deviceLabel().trim());
        session.setAttribute(AuthSessionAttributes.CLIENT_TYPE, clientType.name());
        session.setAttribute(AuthSessionAttributes.IP_ADDRESS, ip);
        // Display only (admin session list); never used for authorization.
        session.setAttribute(AuthSessionAttributes.USER_AGENT, truncate(request.getHeader("User-Agent"), 300));
        session.setAttribute(AuthSessionAttributes.ABSOLUTE_EXPIRES_AT, Instant.now().plus(absolute).toEpochMilli());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        auditService.record(AuditService.AUTH_LOGIN_SUCCESS, "USER",
                user.getUserId(), user.getUserId(), correlationId, metadata);

        return toLoginResponse(user);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session != null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ip", ClientIp.resolve(request));
            Object deviceId = session.getAttribute(AuthSessionAttributes.DEVICE_ID);
            if (deviceId != null) {
                metadata.put("deviceId", deviceId.toString());
            }
            UUID userId = userIdFromSession(session);
            if (userId != null) {
                auditService.record(AuditService.AUTH_LOGOUT, "USER",
                        userId, userId, correlationId(request), metadata);
            }
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (authentication != null) {
            log.debug("User logged out");
        }
    }

    public MeResponse me(Authentication authentication, HttpSession session) {
        // Re-check that the account still exists and is active on every /me call.
        Users user = usersRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));

        UUID deviceId = null;
        Object device = session.getAttribute(AuthSessionAttributes.DEVICE_ID);
        if (device != null) {
            deviceId = UUID.fromString(device.toString());
        }

        long idleExpiry = session.getLastAccessedTime() + session.getMaxInactiveInterval() * 1000L;
        Object absolute = session.getAttribute(AuthSessionAttributes.ABSOLUTE_EXPIRES_AT);
        long expiry = absolute instanceof Long millis ? Math.min(idleExpiry, millis) : idleExpiry;

        return new MeResponse(user.getUserId(), user.getUserName(), user.getEmail(), user.getRole(),
                RolePermissions.getPermissionsByRole(user.getRole()), deviceId, Instant.ofEpochMilli(expiry));
    }

    private void handleFailure(Optional<Users> existing, AuthenticationException ex, Map<String, Object> metadata, String correlationId) {
        Map<String, Object> failed = new HashMap<>(metadata);
        failed.put("knownUser", existing.isPresent());
        failed.put("reason", ex.getClass().getSimpleName());
        UUID userId = existing.map(Users::getUserId).orElse(null);

        auditService.record(AuditService.AUTH_LOGIN_FAILED, "USER", userId, userId, correlationId, failed);

        if (userId != null) {
            LoginAttemptService.FailureResult result = loginAttemptService.registerFailure(userId);
            if (result.lockedNow()) {
                Map<String, Object> locked = new HashMap<>(metadata);
                locked.put("lockedUntil", result.lockedUntil().toString());
                auditService.record(AuditService.AUTH_ACCOUNT_LOCKED, "USER", userId, userId, correlationId, locked);
            }
        }
    }

    private LoginResponse toLoginResponse(Users user) {
        return new LoginResponse(user.getUserId(), user.getUserName(), user.getEmail(), user.getRole(),
                RolePermissions.getPermissionsByRole(user.getRole()));
    }

    private static boolean isLocked(Users user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private static UUID userIdFromSession(HttpSession session) {
        Object value = session.getAttribute(AuthSessionAttributes.USER_ID);
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID);
        return value == null ? null : value.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

}
