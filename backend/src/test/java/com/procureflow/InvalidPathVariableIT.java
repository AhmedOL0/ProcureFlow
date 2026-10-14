package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Malformed path variables are client faults, not server faults: a non-UUID
 * value on a UUID route answers 400 with the problem envelope, never 500.
 * Regression: before the account endpoints landed, GET /api/v1/users/me fell
 * through to /users/{id} and UUID conversion of "me" answered
 * INTERNAL_ERROR with a stack trace in the logs. The literal /me mapping
 * now owns that path, so this suite pins the contract with other non-UUID
 * values instead.
 */
class InvalidPathVariableIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void nonUuidUserIdAnswersBadRequest() {
        String token = provision(uniqueSlug("acme"));

        ResponseEntity<String> response = get("/api/v1/users/not-a-uuid", token, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("INVALID_PATH_VARIABLE"));
    }

    @Test
    void nonUuidSupplierIdAnswersBadRequest() {
        String token = provision(uniqueSlug("globex"));

        ResponseEntity<String> response = get("/api/v1/suppliers/not-a-uuid", token, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("INVALID_PATH_VARIABLE"));
    }

    @Test
    void unknownPublicPathAnswersNotFound() {
        // /swagger-ui/** is permitAll, so an unmapped asset falls through to
        // the resource handler instead of the auth layer: still a 404 with
        // the envelope, never a 500. (Unknown authenticated paths answer
        // 401/404 through the security chain by design.)
        ResponseEntity<String> response =
                rest.getForEntity("/swagger-ui/no-such-asset", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("NOT_FOUND"));
    }

    @Test
    void responsesCarryRequestCorrelation() {        // Generated when absent so every response (even 404s) is traceable.
        ResponseEntity<String> anonymous =
                rest.getForEntity("/swagger-ui/no-such-asset", String.class);
        assertNotNull(anonymous.getHeaders().getFirst("X-Request-Id"));

        // Caller-provided ids echo back for end-to-end tracing.
        HttpHeaders echo = new HttpHeaders();
        echo.set("X-Request-Id", "probe-123");
        ResponseEntity<String> traced = rest.exchange(
                "/swagger-ui/no-such-asset", HttpMethod.GET, new HttpEntity<>(echo), String.class);
        assertEquals("probe-123", traced.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void livenessProbeIsPublic() {
        // Container orchestration probes without credentials; only statuses
        // leak (details stay authorized).
        ResponseEntity<String> liveness = rest.getForEntity("/actuator/health/liveness", String.class);
        assertEquals(HttpStatus.OK, liveness.getStatusCode());
        assertNotNull(liveness.getBody());
        assertTrue(liveness.getBody().contains("\"status\":\"UP\""));
    }

    // NOTE: no IT asserts /actuator/prometheus is public. Spring Boot
    // excludes metrics-export auto-configurations from test contexts by
    // design (see MetricsIT javadoc), so the scrape endpoint is never
    // mapped under @SpringBootTest — the allowlist entry in SecurityConfig
    // is verified live instead (Prometheus targets UP + anonymous curl).

    private String provision(String slug) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", "boss@" + slug + ".test",
                        "password", "correct-horse-123",
                        "tenantSlug", slug,
                        "tenantName", "Workspace " + slug),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().accessToken();
    }
}
