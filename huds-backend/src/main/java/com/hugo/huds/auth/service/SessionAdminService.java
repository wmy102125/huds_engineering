package com.hugo.huds.auth.service;

import org.springframework.stereotype.Service;
import com.hugo.huds.audit.AuditService;
import com.hugo.huds.auth.dto.SessionInfoResponse;
import com.hugo.huds.auth.support.AuthSessionAttributes;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.BusinessException;
import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.repository.UsersRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * @author meiyan wang
 * @date 2026-10-16 1:35 p.m.
 * @description
 * Administrator operations on user sessions and passwords: list active sessions, revoke one
 * (lost device) and reset a password (which revokes every session of that user).
 */
@Service
public class SessionAdminService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SessionAdminService(FindByIndexNameSessionRepository<? extends Session> sessionRepository,
                               UsersRepository usersRepository,
                               PasswordEncoder passwordEncoder,
                               AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<SessionInfoResponse> listSessions(UUID userId) {
        Users user = requireUser(userId);
        List<SessionInfoResponse> result = new ArrayList<>();
        for (Session session : sessionsOf(user).values()) {
            result.add(toInfo(session));
        }
        return result;
    }

    public void revokeSession(UUID actorUserId, UUID userId, String sessionRef, String correlationId) {
        Users user = requireUser(userId);
        for (Session session : sessionsOf(user).values()) {
            if (ref(session.getId()).equals(sessionRef)) {
                String deviceId = attr(session, AuthSessionAttributes.DEVICE_ID);
                sessionRepository.deleteById(session.getId());
                auditService.record(AuditService.AUTH_SESSION_REVOKED, "USER", actorUserId, userId,
                        correlationId, Map.of("deviceId", deviceId, "sessionRef", sessionRef));
                return;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "Session not found.");
    }

    @Transactional
    public void resetPassword(UUID actorUserId, UUID userId, String newPassword, String correlationId) {
        Users user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        usersRepository.save(user);

        // A reset must end every existing login of that user, on every device.
        int revoked = 0;
        for (Session session : sessionsOf(user).values()) {
            sessionRepository.deleteById(session.getId());
            revoked++;
        }
        auditService.record(AuditService.AUTH_PASSWORD_RESET, "USER", actorUserId, userId,
                correlationId, Map.of("sessionsRevoked", revoked));
    }

    // Spring Session indexes JDBC sessions by the authenticated principal name (the user's email).
    private Map<String, ? extends Session> sessionsOf(Users user) {
        return sessionRepository.findByPrincipalName(user.getEmail());
    }

    private Users requireUser(UUID userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found."));
    }

    private SessionInfoResponse toInfo(Session session) {
        Instant idleExpiry = session.getLastAccessedTime().plus(session.getMaxInactiveInterval());
        Object absolute = session.getAttribute(AuthSessionAttributes.ABSOLUTE_EXPIRES_AT);
        Instant expiry = idleExpiry;
        if (absolute instanceof Long millis && Instant.ofEpochMilli(millis).isBefore(idleExpiry)) {
            expiry = Instant.ofEpochMilli(millis);
        }
        return new SessionInfoResponse(
                ref(session.getId()),
                attr(session, AuthSessionAttributes.DEVICE_ID),
                attr(session, AuthSessionAttributes.DEVICE_LABEL),
                attr(session, AuthSessionAttributes.CLIENT_TYPE),
                attr(session, AuthSessionAttributes.IP_ADDRESS),
                attr(session, AuthSessionAttributes.USER_AGENT),
                session.getCreationTime(),
                session.getLastAccessedTime(),
                expiry);
    }

    private static String attr(Session session, String name) {
        Object value = session.getAttribute(name);
        return value == null ? "" : value.toString();
    }

    /** One-way reference: lets an admin pick a session without learning the real session id. */
    static String ref(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
