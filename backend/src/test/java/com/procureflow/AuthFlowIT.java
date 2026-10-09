package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * End-to-end authentication journeys against the running API: tenant
 * provisioning on register, login (including multi-tenant disambiguation),
 * refresh rotation with theft detection, logout, and the failure paths.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void registerCreatesTenantAndAdminWithTokens() {
        String slug = uniqueSlug("acme");

        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", "ada@" + slug + ".test",
                        "password", "correct-horse-123",
                        "firstName", "Ada",
                        "tenantSlug", slug,
                        "tenantName", "Acme Inc"),
                AuthResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AuthResponse body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.accessToken());
        assertNotNull(body.refreshToken());
        assertEquals("Bearer", body.tokenType());
        assertEquals(slug, body.user().tenantSlug());
        assertTrue(body.user().roles().contains("TENANT_ADMIN"));
    }

    @Test
    void registerJoinsExistingTenantAsMember() {
        String slug = uniqueSlug("acme");
        register(slug, "boss@" + slug + ".test");

        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", "min@" + slug + ".test",
                        "password", "correct-horse-123",
                        "tenantSlug", slug),
                AuthResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().user().roles().contains("MEMBER"));
    }

    @Test
    void duplicateEmailInSameTenantIsConflict() {
        String slug = uniqueSlug("acme");
        register(slug, "ada@" + slug + ".test");

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", "ada@" + slug + ".test",
                        "password", "correct-horse-123",
                        "tenantSlug", slug),
                String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("EMAIL_IN_USE"));
    }

    @Test
    void weakPasswordIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", "tiny@" + uniqueSlug("acme") + ".test",
                        "password", "short",
                        "tenantSlug", uniqueSlug("acme")),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void loginSucceedsAndWrongPasswordFails() {
        String slug = uniqueSlug("acme");
        String email = "ada@" + slug + ".test";
        register(slug, email);

        ResponseEntity<AuthResponse> ok = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", "correct-horse-123"), AuthResponse.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertNotNull(ok.getBody());
        assertNotNull(ok.getBody().accessToken());

        ResponseEntity<String> denied = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", "wrong-password-1"), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
    }

    @Test
    void sameEmailInTwoTenantsRequiresDisambiguation() {
        String email = "sam@" + uniqueSlug("shared") + ".test";
        String slugA = uniqueSlug("alpha");
        String slugB = uniqueSlug("beta");
        register(slugA, email);
        register(slugB, email);

        ResponseEntity<String> ambiguous = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", "correct-horse-123"), String.class);
        assertEquals(HttpStatus.CONFLICT, ambiguous.getStatusCode());
        assertNotNull(ambiguous.getBody());
        assertTrue(ambiguous.getBody().contains("LOGIN_AMBIGUOUS"));

        ResponseEntity<AuthResponse> scoped = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "correct-horse-123", "tenantSlug", slugB),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, scoped.getStatusCode());
        assertNotNull(scoped.getBody());
        assertEquals(slugB, scoped.getBody().user().tenantSlug());
    }

    @Test
    void meNeedsAToken() {
        String slug = uniqueSlug("acme");
        AuthResponse auth = register(slug, "ada@" + slug + ".test");

        ResponseEntity<String> me = get("/api/v1/auth/me", auth.accessToken(), String.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertNotNull(me.getBody());
        assertTrue(me.getBody().contains("ada@" + slug + ".test"));

        ResponseEntity<String> anonymous = rest.getForEntity("/api/v1/auth/me", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
    }

    @Test
    void refreshRotatesAndSpentTokenTriggersTheftResponse() {
        String slug = uniqueSlug("acme");
        AuthResponse auth = register(slug, "ada@" + slug + ".test");

        ResponseEntity<AuthResponse> rotated = rest.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", auth.refreshToken()), AuthResponse.class);
        assertEquals(HttpStatus.OK, rotated.getStatusCode());
        assertNotNull(rotated.getBody());
        assertNotEquals(auth.refreshToken(), rotated.getBody().refreshToken());

        ResponseEntity<String> replay = rest.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", auth.refreshToken()), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, replay.getStatusCode());
        assertNotNull(replay.getBody());
        assertTrue(replay.getBody().contains("TOKEN_REUSED"));

        // After theft detection the password still works: a fresh chain starts.
        ResponseEntity<AuthResponse> relogin = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ada@" + slug + ".test", "password", "correct-horse-123"),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, relogin.getStatusCode());
    }

    @Test
    void logoutRevokesTheToken() {
        String slug = uniqueSlug("acme");
        AuthResponse auth = register(slug, "ada@" + slug + ".test");

        ResponseEntity<Void> logout = rest.postForEntity(
                "/api/v1/auth/logout",
                new HttpEntity<>(Map.of("refreshToken", auth.refreshToken())),
                Void.class);
        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode());

        ResponseEntity<String> after = rest.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", auth.refreshToken()), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, after.getStatusCode());
    }

    @Test
    void malformedJsonIs400Not500() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/login", new HttpEntity<>("{not-json", headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("MALFORMED_JSON"));
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
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
