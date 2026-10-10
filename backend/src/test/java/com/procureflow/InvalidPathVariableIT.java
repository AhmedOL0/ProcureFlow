package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Malformed path variables are client faults, not server faults: a non-UUID
 * value on a UUID route answers 400 with the problem envelope, never 500.
 * Regression: GET /api/v1/users/me — called by Account Settings before the
 * account endpoints land — fell through to INTERNAL_ERROR with a stack trace
 * in the logs.
 */
class InvalidPathVariableIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void nonUuidUserIdAnswersBadRequest() {
        String token = provision(uniqueSlug("acme"));

        ResponseEntity<String> response = get("/api/v1/users/me", token, String.class);

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
