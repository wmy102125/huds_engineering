package com.hugo.huds.support;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 12:20 p.m.
 * @description
 * Base class for integration tests that need a real PostgreSQL(TestContainer),CSRF handling and a logged-in user.
 * Requires Docker
 *
 * <p>Every test method starts with an ADMIN user already logged in ({@link #session}). Use
 * {@link #authed(MockHttpServletRequestBuilder)} to attach the session and CSRF credentials to a request,
 * or {@link #loginAs(String)} to act as another role.
 *
 * <p>The database is shared by all tests and is NOT cleaned between them, so tests must create their own
 * unique data (random emails, UUIDs) and must not assume empty tables.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "huds.auth.ip-failure-limit=1000",
        "huds.auth.max-failed-attempts=5"
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {
    protected static final String PASSWORD = "correct-horse-battery";

    /**
     * Singleton container shared by ALL test classes in the JVM. A per-class container would get a new port for
     * each class while Spring's cached application context still points at the old one.
     * Testcontainers' Ryuk sidecar removes the container when the JVM exits.
     */
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        // Called only when a Spring context is actually built, so tests skipped for lack of Docker never reach it.
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder passwordEncoder;

    /** Session cookie of the ADMIN user created before each test. */
    protected Cookie session;
    /** User id of the ADMIN user created before each test. */
    protected UUID adminUserId;

    @BeforeEach
    void loginDefaultAdmin() throws Exception {
        adminUserId = createUser("ADMIN");
        session = loginOk(adminUserId, UUID.randomUUID(), "DESKTOP");
    }

    // ---------- CSRF ----------

    protected record Csrf(Cookie cookie, String header, String token) {
    }

    protected Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "XSRF-TOKEN cookie must be set");
        return new Csrf(cookie, body.get("headerName").asText(), body.get("token").asText());
    }

    // ---------- users and login ----------

    protected UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, user_name, email, password_hash, role, active) VALUES (?,?,?,?,?,true)",
                id, "Test " + role, emailOf(id), passwordEncoder.encode(PASSWORD), role);
        return id;
    }

    protected String emailOf(UUID userId) {
        return userId + "@example.com";
    }

    /** Performs a login and returns the raw result, whatever the status (for negative tests). */
    protected MvcResult login(String email, String password, UUID deviceId, String clientType) throws Exception {
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

    /** Logs in an existing test user (created with {@link #createUser}) and returns its session cookie. */
    protected Cookie loginOk(UUID userId, UUID deviceId, String clientType) throws Exception {
        MvcResult result = login(emailOf(userId), PASSWORD, deviceId, clientType);
        assertEquals(200, result.getResponse().getStatus());
        // MockMvc has no servlet container, so the cookie keeps Spring Session's default name.
        Cookie sessionCookie = Arrays.stream(result.getResponse().getCookies())
                .filter(c -> !"XSRF-TOKEN".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(sessionCookie, "login must set a session cookie; Set-Cookie headers were: "
                + result.getResponse().getHeaders("Set-Cookie"));
        return sessionCookie;
    }

    /** Creates a fresh user with the given role, logs it in and returns its session cookie. */
    protected Cookie loginAs(String role) throws Exception {
        return loginOk(createUser(role), UUID.randomUUID(), "DESKTOP");
    }

    // ---------- authenticated requests ----------

    /** Adds the default admin session plus CSRF cookie/header to the request. */
    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) throws Exception {
        return authed(request, session);
    }

    /** Adds the given session plus CSRF cookie/header to the request. */
    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Cookie sessionCookie)
            throws Exception {
        Csrf csrf = csrf();
        return request.cookie(sessionCookie, csrf.cookie()).header(csrf.header(), csrf.token());
    }

    /** Adds only the CSRF cookie/header (no session), to test "logged out" write requests. */
    protected MockHttpServletRequestBuilder withCsrfOnly(MockHttpServletRequestBuilder request) throws Exception {
        Csrf csrf = csrf();
        return request.cookie(csrf.cookie()).header(csrf.header(), csrf.token());
    }

    // ---------- misc helpers ----------

    protected String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode jsonOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
