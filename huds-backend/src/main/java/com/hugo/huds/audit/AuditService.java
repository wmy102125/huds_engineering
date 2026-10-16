package com.hugo.huds.audit;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
/**
 * @author meiyan wang
 * @date 2026-10-16 11:07 a.m.
 * Appends rows to {@code audit_events}. Each write runs in its own transaction, so a failed login
 * or a rolled-back request still leaves an audit trail. Never put passwords, tokens or session ids
 * in {@code metadata}.
 */
@Service
public class AuditService {

    public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
    public static final String AUTH_LOGIN_BLOCKED = "AUTH_LOGIN_BLOCKED";
    public static final String AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String AUTH_SESSION_REVOKED = "AUTH_SESSION_REVOKED";
    public static final String AUTH_PASSWORD_RESET = "AUTH_PASSWORD_RESET";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String entityType, UUID actorUserId, UUID entityId,
                       String correlationId, Map<String, Object> metadata){
        String json;
        try {
            json = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JacksonException e) {
            log.warn("Could not serialise audit metadata for {}", eventType, e);
            json = "{}";
        }
        jdbcTemplate.update(
                "INSERT INTO audit_events "
                        + "(actor_user_id, event_type, entity_type, entity_id, correlation_id, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                actorUserId, eventType, entityType, entityId, correlationId, json);
    }

    /** Failed logins from one IP inside the window; used for the per-IP rate limit. */
    @Transactional(readOnly = true)
    public long countRecentLoginFailuresByIp(String ip, Duration window) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(window));
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events "
                        + "WHERE event_type = ? AND metadata ->> 'ip' = ? AND occurred_at > ?",
                Long.class, AUTH_LOGIN_FAILED, ip, cutoff);
        return count == null ? 0 : count;
    }
}
