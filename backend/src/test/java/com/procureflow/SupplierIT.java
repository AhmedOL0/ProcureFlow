package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.util.List;
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
 * Supplier management journeys: tenant-scoped CRUD, role gates, the
 * single-primary-contact invariant, category assignment with in-use
 * protection, and monthly scorecards.
 */
class SupplierIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void adminCanManageSuppliersEndToEnd() {
        Fixture admin = provision(uniqueSlug("acme"));

        UUID id = createSupplier(admin.token(), "Acme Parts");
        assertEquals(HttpStatus.OK, get("/api/v1/suppliers/" + id, admin.token(), String.class).getStatusCode());

        ResponseEntity<String> duplicate = post(
                "/api/v1/suppliers", admin.token(), Map.of("name", "Acme Parts"), String.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        ResponseEntity<String> search = get("/api/v1/suppliers?q=acme", admin.token(), String.class);
        assertEquals(HttpStatus.OK, search.getStatusCode());
        assertNotNull(search.getBody());
        assertTrue(search.getBody().contains("Acme Parts"));
    }

    @Test
    void memberCanReadButNotWrite() {
        Fixture admin = provision(uniqueSlug("acme"));
        AuthResponse member = joinAsMember(admin.slug(), "min@" + admin.slug() + ".test");

        assertEquals(HttpStatus.OK, get("/api/v1/suppliers", member.accessToken(), String.class).getStatusCode());

        ResponseEntity<String> denied = post(
                "/api/v1/suppliers", member.accessToken(), Map.of("name", "Rogue"), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    }

    @Test
    void officerCanWriteSuppliers() {
        Fixture admin = provision(uniqueSlug("acme"));
        String email = "officer@" + admin.slug() + ".test";
        post("/api/v1/users", admin.token(),
                Map.of(
                        "email", email,
                        "password", "correct-horse-123",
                        "roleNames", List.of("OFFICER")),
                String.class);
        AuthResponse officer = login(email, null);

        ResponseEntity<Map> created =
                post("/api/v1/suppliers", officer.accessToken(), Map.of("name", "Officer Supply"), Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
    }

    @Test
    void suppliersAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID id = createSupplier(tenantA.token(), "Acme Parts");

        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/suppliers/" + id, tenantB.token(), String.class).getStatusCode());

        ResponseEntity<String> list = get("/api/v1/suppliers", tenantB.token(), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(!list.getBody().contains("Acme Parts"));
    }

    @Test
    void onlyOnePrimaryContactPerSupplier() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");

        post("/api/v1/suppliers/" + supplier + "/contacts", admin.token(),
                Map.of("name", "First", "primary", true), String.class);
        post("/api/v1/suppliers/" + supplier + "/contacts", admin.token(),
                Map.of("name", "Second", "primary", true), String.class);

        ResponseEntity<List> contacts =
                get("/api/v1/suppliers/" + supplier + "/contacts", admin.token(), List.class);
        assertEquals(HttpStatus.OK, contacts.getStatusCode());
        assertNotNull(contacts.getBody());
        assertEquals(2, contacts.getBody().size());
        long primaries = contacts.getBody().stream()
                .filter(c -> Boolean.TRUE.equals(((Map<?, ?>) c).get("primary")))
                .count();
        assertEquals(1, primaries);
    }

    @Test
    void categoriesAssignReplaceAndProtectInUse() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID hardware = createCategory(admin.token(), "Hardware");
        UUID catering = createCategory(admin.token(), "Catering");

        ResponseEntity<List> assigned = post(
                "/api/v1/suppliers/" + supplier + "/categories",
                admin.token(),
                Map.of("categoryIds", List.of(hardware, catering)),
                List.class);
        assertEquals(HttpStatus.OK, assigned.getStatusCode());
        assertNotNull(assigned.getBody());
        assertEquals(2, assigned.getBody().size());

        ResponseEntity<List> replaced = post(
                "/api/v1/suppliers/" + supplier + "/categories",
                admin.token(),
                Map.of("categoryIds", List.of(catering)),
                List.class);
        assertEquals(HttpStatus.OK, replaced.getStatusCode());
        assertNotNull(replaced.getBody());
        assertEquals(1, replaced.getBody().size());

        ResponseEntity<String> inUse = delete("/api/v1/supplier-categories/" + catering, admin.token());
        assertEquals(HttpStatus.CONFLICT, inUse.getStatusCode());

        assertEquals(
                HttpStatus.NO_CONTENT, delete("/api/v1/supplier-categories/" + hardware, admin.token()).getStatusCode());
    }

    @Test
    void scorecardsAreMonthlyUniqueAndValidated() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");

        ResponseEntity<Map> recorded = post(
                "/api/v1/suppliers/" + supplier + "/performances",
                admin.token(),
                Map.of("period", "2026-09", "onTimeRate", 97.5, "qualityScore", 88.0),
                Map.class);
        assertEquals(HttpStatus.CREATED, recorded.getStatusCode());

        ResponseEntity<String> duplicate = post(
                "/api/v1/suppliers/" + supplier + "/performances",
                admin.token(),
                Map.of("period", "2026-09"),
                String.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        ResponseEntity<String> badPeriod = post(
                "/api/v1/suppliers/" + supplier + "/performances",
                admin.token(),
                Map.of("period", "September"),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, badPeriod.getStatusCode());

        ResponseEntity<List> history =
                get("/api/v1/suppliers/" + supplier + "/performances", admin.token(), List.class);
        assertEquals(HttpStatus.OK, history.getStatusCode());
        assertNotNull(history.getBody());
        assertEquals(1, history.getBody().size());
    }

    private Fixture provision(String slug) {
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
        return new Fixture(slug, response.getBody().accessToken());
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

    private AuthResponse login(String email, String tenantSlug) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("password", "correct-horse-123");
        if (tenantSlug != null) {
            body.put("tenantSlug", tenantSlug);
        }
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/v1/auth/login", body, AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private UUID createSupplier(String token, String name) {
        ResponseEntity<Map> response = post("/api/v1/suppliers", token, Map.of("name", name), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID createCategory(String token, String name) {
        ResponseEntity<Map> response =
                post("/api/v1/supplier-categories", token, Map.of("name", name), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private ResponseEntity<String> delete(String url, String token) {
        return rest.exchange(url, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), String.class);
    }

    private record Fixture(String slug, String token) {
    }
}
