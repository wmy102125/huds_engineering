package com.hugo.huds.audit.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.AuditorAware;
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
 * Appends rows to {@code audit_events}.
 *
 * <p>{@link  #record(String, String, UUID, UUID, String, Map)} runs in its own transaction, so a failed login
 *      or a rolled-back request still leaves an audit trail.
 *    {@link #recordInTransaction(AuditAction, String, UUID, Map)} joins the caller's transaction,
 *    so a business change and its audit event commit or roll back together.</p>
 * <p>Never put passwords, tokens or session ids in {@code metadata}.</p>
 *
 * @author Meiyan Wang
 * @date 2026-10-16 11:07 a.m.
 */
@Service
@Slf4j
public class AuditService {

    public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
    public static final String AUTH_LOGIN_BLOCKED = "AUTH_LOGIN_BLOCKED";
    public static final String AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String AUTH_SESSION_REVOKED = "AUTH_SESSION_REVOKED";
    public static final String AUTH_PASSWORD_RESET = "AUTH_PASSWORD_RESET";


    private static final String INSERT_SQL =
            "INSERT INTO audit_events "
                    + "(actor_user_id, event_type, entity_type, entity_id, correlation_id, metadata) "
                    + "VALUES (?, ?, ?, ?, ?, ?::jsonb)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditorAware<UUID> auditorAware;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AuditorAware<UUID> auditorAware) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditorAware = auditorAware;
    }


    /**
     * Writes an event in its own transaction (used for authentication events).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String entityType, UUID actorUserId, UUID entityId,
                       String correlationId, Map<String, Object> metadata) {
        jdbcTemplate.update(INSERT_SQL, actorUserId, eventType, entityType, entityId, correlationId, toJson(eventType,metadata));
    }

    /**
     * Writes a business audit event inside the caller's transaction.
     * The actor and correlationId are taken from the current request, so callers only describe what happened.
     *
     *<p>MANDATORY: it must be called from an existing transaction,
     * otherwise Spring fails fast instead of silently writing the event in a separate transaction.</p>
     * @param auditAction what happened
     * @param entityType the audited entity type, for example {@code CUSTOMER}
     * @param entityId the audited entity id, for example customerId
     * @param metadata extra details such as changed field names (no personal data); may be empty
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInTransaction(AuditAction auditAction, String entityType, UUID entityId, Map<String, Object> metadata) {
        String eventType = entityType + "_" + auditAction.name();
        UUID actorUserId = auditorAware.getCurrentAuditor().orElse(null);
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID);
        jdbcTemplate.update(INSERT_SQL, actorUserId, eventType, entityType, entityId, correlationId,toJson(eventType,metadata));
    }


    /**
     * Failed logins from one IP inside the window; used for the per-IP rate limit.
     */
    @Transactional(readOnly = true)
    public long countRecentLoginFailuresByIp(String ip, Duration window) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(window));
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events "
                        + "WHERE event_type = ? AND metadata ->> 'ip' = ? AND occurred_at > ?",
                Long.class, AUTH_LOGIN_FAILED, ip, cutoff);
        return count == null ? 0 : count;
    }

    private String toJson(String eventType, Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JacksonException e) {
            log.warn("Could not serialise audit metadata for {}", eventType, e);
            return "{}";
        }
    }
}
