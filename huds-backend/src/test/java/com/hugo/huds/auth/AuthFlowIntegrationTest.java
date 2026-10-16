package com.hugo.huds.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the ADR-013 acceptance list against a real PostgreSQL (Testcontainers),
 * so the Flyway migrations and the Spring Session JDBC tables are exercised too. Requires Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "huds.auth.ip-failure-limit=1000",
        "huds.auth.max-failed-attempts=5"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;



    // ---------- helpers ----------

    private record Csrf(Cookie cookie, String header, String token) {
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "XSRF-TOKEN cookie must be set");
        return new Csrf(cookie, body.get("headerName").asText(), body.get("token").asText());
    }

    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, user_name, email, password_hash, role, active) VALUES (?,?,?,?,?,true)",
                id, "Test " + role, id + "@example.com", passwordEncoder.encode(PASSWORD), role);
        return id;
    }

    private String emailOf(UUID userId) {
        return userId + "@example.com";
    }

    private MvcResult login(String email, String password, UUID deviceId, String clientType) throws Exception {
        Csrf csrf = csrf();
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", password,
                "deviceId", deviceId.toString(), "clientType", clientType));
        return mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private Cookie loginOk(UUID userId, UUID deviceId, String clientType) throws Exception {
        MvcResult result = login(emailOf(userId), PASSWORD, deviceId, clientType);
        assertEquals(200, result.getResponse().getStatus());
        // MockMvc has no servlet container, so the cookie keeps Spring Session's default name.
        // Name and flags (HADS_SESSION, HttpOnly, SameSite) are verified against the real server by auth-smoke.sh.
        Cookie session = Arrays.stream(result.getResponse().getCookies())
                .filter(c -> !"XSRF-TOKEN".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(session, "login must set a session cookie; Set-Cookie headers were: "
                + result.getResponse().getHeaders("Set-Cookie"));
        return session;
    }

    private long auditCount(String eventType, UUID userId) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE event_type = ? AND entity_id = ?",
                Long.class, eventType, userId);
        return n == null ? 0 : n;
    }

    // ---------- tests ----------

    @Test
    void loginSucceedsAndSessionAuthenticatesMe() throws Exception {
        UUID user = createUser("TECHNICIAN");
        Cookie session = loginOk(user, UUID.randomUUID(), "DESKTOP");

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailOf(user)))
                .andExpect(jsonPath("$.role").value("TECHNICIAN"));

        assertEquals(1, auditCount("AUTH_LOGIN_SUCCESS", user));
    }

    @Test
    void wrongPasswordAndUnknownUserBothReturnTheSame401NotA500() throws Exception {
        UUID user = createUser("TECHNICIAN");

        MvcResult wrong = login(emailOf(user), "not-the-password", UUID.randomUUID(), "DESKTOP");
        MvcResult unknown = login("nobody-" + UUID.randomUUID() + "@example.com", "whatever-12345", UUID.randomUUID(), "DESKTOP");

        assertEquals(401, wrong.getResponse().getStatus());
        assertEquals(401, unknown.getResponse().getStatus());
        JsonNode a = objectMapper.readTree(wrong.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(unknown.getResponse().getContentAsString());
        assertEquals("UNAUTHENTICATED", a.get("code").asText());
        assertEquals(a.get("message").asText(), b.get("message").asText());
        assertTrue(auditCount("AUTH_LOGIN_FAILED", user) >= 1);
    }

    @Test
    void unauthenticatedRequestGetsUnifiedBody() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void writeWithoutCsrfTokenIsRejectedAndMarkedRetryable() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void accountLocksAfterFiveFailuresThenReturns429() throws Exception {
        UUID user = createUser("TECHNICIAN");
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(emailOf(user), "bad-password-" + i, UUID.randomUUID(), "DESKTOP").getResponse().getStatus());
        }
        // Even the correct password is refused while locked.
        MvcResult locked = login(emailOf(user), PASSWORD, UUID.randomUUID(), "DESKTOP");
        assertEquals(429, locked.getResponse().getStatus());
        assertEquals("RATE_LIMITED", objectMapper.readTree(locked.getResponse().getContentAsString()).get("code").asText());
        assertEquals(1, auditCount("AUTH_ACCOUNT_LOCKED", user));

        // Lock expiry restores access.
        jdbc.update("UPDATE users SET locked_until = now() - interval '1 minute' WHERE user_id = ?", user);
        assertEquals(200, login(emailOf(user), PASSWORD, UUID.randomUUID(), "DESKTOP").getResponse().getStatus());
    }

    @Test
    void phoneAndDesktopCanBeSignedInAtTheSameTime() throws Exception {
        UUID user = createUser("TECHNICIAN");
        Cookie desktop = loginOk(user, UUID.randomUUID(), "DESKTOP");
        Cookie phone = loginOk(user, UUID.randomUUID(), "MOBILE");

        mockMvc.perform(get("/api/v1/auth/me").cookie(desktop)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isOk());
    }

    @Test
    void adminRevokesOneDeviceAndOthersStayOnline() throws Exception {
        UUID technician = createUser("TECHNICIAN");
        UUID admin = createUser("ADMIN");

        UUID lostDevice = UUID.randomUUID();
        Cookie lost = loginOk(technician, lostDevice, "MOBILE");
        Cookie kept = loginOk(technician, UUID.randomUUID(), "DESKTOP");
        Cookie adminSession = loginOk(admin, UUID.randomUUID(), "DESKTOP");

        MvcResult listing = mockMvc.perform(get("/api/v1/admin/users/" + technician + "/sessions").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        String ref = null;
        for (JsonNode node : objectMapper.readTree(listing.getResponse().getContentAsString())) {
            if (node.get("deviceId").asText().equals(lostDevice.toString())) {
                ref = node.get("sessionRef").asText();
            }
        }
        assertNotNull(ref, "the lost device must appear in the session list");

        Csrf csrf = csrf();
        mockMvc.perform(delete("/api/v1/admin/users/" + technician + "/sessions/" + ref)
                        .cookie(adminSession, csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(lost)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").cookie(kept)).andExpect(status().isOk());
        assertEquals(1, auditCount("AUTH_SESSION_REVOKED", technician));
    }

    @Test
    void technicianCannotUseAdminEndpoints() throws Exception {
        UUID technician = createUser("TECHNICIAN");
        Cookie session = loginOk(technician, UUID.randomUUID(), "DESKTOP");

        mockMvc.perform(get("/api/v1/admin/users/" + technician + "/sessions").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void logoutEndsTheSessionAndIsAudited() throws Exception {
        UUID user = createUser("TECHNICIAN");
        Cookie session = loginOk(user, UUID.randomUUID(), "DESKTOP");

        Csrf csrf = csrf();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(session, csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        assertEquals(1, auditCount("AUTH_LOGOUT", user));
    }

    @Test
    void passwordResetRevokesAllSessionsAndAllowsLoginWithNewPassword() throws Exception {
        UUID technician = createUser("TECHNICIAN");
        UUID admin = createUser("ADMIN");
        Cookie old = loginOk(technician, UUID.randomUUID(), "DESKTOP");
        Cookie adminSession = loginOk(admin, UUID.randomUUID(), "DESKTOP");

        Csrf csrf = csrf();
        mockMvc.perform(post("/api/v1/admin/users/" + technician + "/password")
                        .cookie(adminSession, csrf.cookie())
                        .header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"a-brand-new-password\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(old)).andExpect(status().isUnauthorized());
        assertEquals(401, login(emailOf(technician), PASSWORD, UUID.randomUUID(), "DESKTOP").getResponse().getStatus());
        assertEquals(200, login(emailOf(technician), "a-brand-new-password", UUID.randomUUID(), "DESKTOP").getResponse().getStatus());
        assertEquals(1, auditCount("AUTH_PASSWORD_RESET", technician));
    }
}