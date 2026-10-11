package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.procureflow.identity.api.AuthResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Per-route authorization matrix: a plain MEMBER is denied (403) on every
 * authority-gated write and read, stays allowed (200) on authenticated
 * reads, and anonymous callers answer 401 — while admins keep full access
 * (no over-deny). Bodies are valid on purpose: method security runs after
 * bean validation, so only well-formed requests prove the 403 layer.
 */
class AuthorizationMatrixIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void memberIsDeniedAuthorityRoutesButKeepsAuthenticatedReads() {
        String slug = uniqueSlug("matrix");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        String member = loginAsMember(admin.accessToken(), slug);

        assertEquals(
                HttpStatus.FORBIDDEN,
                post("/api/v1/suppliers", member, Map.of("name", "Matrix"), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                        "/api/v1/purchase-orders",
                        member,
                        Map.of("requestId", UUID.randomUUID(), "supplierId", UUID.randomUUID()),
                        String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                        "/api/v1/invoices",
                        member,
                        Map.of(
                                "orderId", UUID.randomUUID(),
                                "number", "M-1",
                                "lines", List.of(Map.of("orderItemId", UUID.randomUUID(), "quantity", 1))),
                        String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                        "/api/v1/budgets",
                        member,
                        Map.of("name", "Matrix", "period", "2026-10", "amountMinor", 1000),
                        String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                        "/api/v1/departments", member, Map.of("name", "Matrix"), String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                        "/api/v1/approvals/workflows",
                        member,
                        Map.of("name", "Matrix", "minAmountMinor", 0, "escalateAfterDays", 3),
                        String.class)
                .getStatusCode());
        assertEquals(
                HttpStatus.FORBIDDEN,
                post("/api/v1/ai/chat", member, Map.of("question", "hi"), String.class).getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN, get("/api/v1/users", member, String.class).getStatusCode());
        assertEquals(
                HttpStatus.FORBIDDEN, get("/api/v1/audit-events", member, String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/v1/budgets", member, String.class).getStatusCode());

        assertEquals(HttpStatus.OK, get("/api/v1/suppliers", member, String.class).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/v1/tenants/current", member, String.class).getStatusCode());
        // MEMBER carries analytics:read by design (dashboard KPIs for everyone).
        assertEquals(HttpStatus.OK, get("/api/v1/analytics/spend", member, String.class).getStatusCode());
    }

    @Test
    void anonymousCallersAnswerUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/api/v1/suppliers", String.class).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                post("/api/v1/suppliers", null, Map.of("name", "Matrix"), String.class).getStatusCode());
    }

    @Test
    void adminsKeepFullAccess() {
        String slug = uniqueSlug("matrix");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");

        assertEquals(HttpStatus.OK, get("/api/v1/users", admin.accessToken(), String.class).getStatusCode());
        assertEquals(
                HttpStatus.CREATED,
                post("/api/v1/departments", admin.accessToken(), Map.of("name", "Matrix"), String.class)
                        .getStatusCode());
    }

    private String loginAsMember(String adminToken, String slug) {
        ResponseEntity<AuthResponse> created = post(
                "/api/v1/users",
                adminToken,
                Map.of(
                        "email", "min@" + slug + ".test",
                        "password", "correct-horse-123",
                        "roleNames", List.of("MEMBER")),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        ResponseEntity<AuthResponse> login = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "min@" + slug + ".test", "password", "correct-horse-123", "tenantSlug", slug),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        return login.getBody().accessToken();
    }

    private AuthResponse register(String tenantSlug, String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", "correct-horse-123",
                        "tenantSlug", tenantSlug,
                        "tenantName", "Workspace " + tenantSlug),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }
}
