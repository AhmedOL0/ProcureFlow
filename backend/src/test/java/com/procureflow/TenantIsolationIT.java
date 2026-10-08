package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Proves tenant isolation at the HTTP layer: reads are scoped, cross-tenant
 * ids answer 404 (never 403, to avoid leaking other tenants' existence),
 * and role checks hold for members without management permissions.
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void departmentsAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));

        UUID engId = createDepartment(tenantA.token(), "Engineering");

        ResponseEntity<String> ownList = get("/api/v1/departments", tenantA.token(), String.class);
        assertEquals(HttpStatus.OK, ownList.getStatusCode());
        assertNotNull(ownList.getBody());
        assertTrue(ownList.getBody().contains("Engineering"));

        ResponseEntity<String> foreignList = get("/api/v1/departments", tenantB.token(), String.class);
        assertEquals(HttpStatus.OK, foreignList.getStatusCode());
        assertNotNull(foreignList.getBody());
        assertTrue(!foreignList.getBody().contains("Engineering"));

        ResponseEntity<String> foreignGet =
                get("/api/v1/departments/" + engId, tenantB.token(), String.class);
        assertEquals(HttpStatus.NOT_FOUND, foreignGet.getStatusCode());
    }

    @Test
    void usersAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));

        ResponseEntity<String> list = get("/api/v1/users", tenantB.token(), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(!list.getBody().contains(tenantA.email()));

        ResponseEntity<String> foreignUser =
                get("/api/v1/users/" + tenantA.userId(), tenantB.token(), String.class);
        assertEquals(HttpStatus.NOT_FOUND, foreignUser.getStatusCode());
    }

    @Test
    void crossTenantWritesAreRejectedAsNotFound() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID engId = createDepartment(tenantA.token(), "Engineering");

        ResponseEntity<String> membership = rest.exchange(
                "/api/v1/memberships",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("userId", tenantB.userId(), "departmentId", engId), bearer(tenantB.token())),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, membership.getStatusCode());
    }

    @Test
    void memberWithoutPermissionCannotManageButCanRead() {
        Fixture tenant = provision(uniqueSlug("acme"));
        AuthResponse member = joinAsMember(tenant.slug(), "min@" + tenant.slug() + ".test");

        ResponseEntity<String> denied = rest.exchange(
                "/api/v1/departments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Rogue"), bearer(member.accessToken())),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        ResponseEntity<String> allowed = get("/api/v1/departments", member.accessToken(), String.class);
        assertEquals(HttpStatus.OK, allowed.getStatusCode());
    }

    private Fixture provision(String slug) {
        String email = "boss@" + slug + ".test";
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", "correct-horse-123",
                        "tenantSlug", slug,
                        "tenantName", "Workspace " + slug),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return new Fixture(slug, email, response.getBody().user().id(), response.getBody().accessToken());
    }

    private AuthResponse joinAsMember(String slug, String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of("email", email, "password", "correct-horse-123", "tenantSlug", slug),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private UUID createDepartment(String token, String name) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/departments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private record Fixture(String slug, String email, UUID userId, String token) {
    }
}
