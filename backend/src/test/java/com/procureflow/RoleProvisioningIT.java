package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.api.UserResponse;
import com.procureflow.identity.application.RoleProvisioningService;
import com.procureflow.identity.infrastructure.RoleRepository;
import com.procureflow.organization.infrastructure.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Persona roles end to end: APPROVER decides but manages no budgets, FINANCE
 * owns pots and payables but decides nothing, AUDITOR reads the trail but
 * transacts nothing. Provisioning is additive — tenants created before a
 * role existed gain it without losing their current grants.
 */
class RoleProvisioningIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Autowired
    private RoleProvisioningService provisioning;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void approverDecidesButManagesNoBudgets() {
        Fixture fixture = registerTenant();
        UserResponse approver = createUser(fixture.adminToken(), fixture.slug(), "approver", "APPROVER");
        String token = login(approver.email());

        ResponseEntity<String> delegation = post(
                "/api/v1/approvals/delegations",
                token,
                Map.of("delegateId", fixture.adminId().toString(),
                        "endsAt", Instant.now().plusSeconds(604800).toString()));
        assertEquals(HttpStatus.CREATED, delegation.getStatusCode());

        ResponseEntity<String> budget = post(
                "/api/v1/budgets",
                token,
                Map.of("name", "sneaky-" + fixture.slug(), "period", "2030-01", "amountMinor", 1000));
        assertEquals(HttpStatus.FORBIDDEN, budget.getStatusCode());
    }

    @Test
    void financeOwnsBudgetsButDecidesNothing() {
        Fixture fixture = registerTenant();
        UserResponse finance = createUser(fixture.adminToken(), fixture.slug(), "finance", "FINANCE");
        String token = login(finance.email());

        ResponseEntity<String> budget = post(
                "/api/v1/budgets",
                token,
                Map.of("name", "fy30-" + fixture.slug(), "period", "2030-01", "amountMinor", 500000));
        assertEquals(HttpStatus.CREATED, budget.getStatusCode());

        ResponseEntity<String> delegation = post(
                "/api/v1/approvals/delegations",
                token,
                Map.of("delegateId", fixture.adminId().toString(),
                        "endsAt", Instant.now().plusSeconds(604800).toString()));
        assertEquals(HttpStatus.FORBIDDEN, delegation.getStatusCode());
    }

    @Test
    void auditorReadsTrailButTransactsNothing() {
        Fixture fixture = registerTenant();
        UserResponse auditor = createUser(fixture.adminToken(), fixture.slug(), "auditor", "AUDITOR");
        String token = login(auditor.email());

        ResponseEntity<String> trail = get("/api/v1/audit-events", token, String.class);
        assertEquals(HttpStatus.OK, trail.getStatusCode());

        ResponseEntity<String> request = post(
                "/api/v1/purchase-requests",
                token,
                Map.of("title", "rogue purchase", "priority", "LOW"));
        assertEquals(HttpStatus.FORBIDDEN, request.getStatusCode());
    }

    @Test
    void provisioningBackfillsMissingRolesWithoutTouchingExisting() {
        Fixture fixture = registerTenant();
        UUID tenantId = tenantRepository.findBySlug(fixture.slug()).orElseThrow().getId();

        roleRepository.findByTenantSlugAndName(fixture.slug(), "AUDITOR").ifPresent(roleRepository::delete);
        roleRepository.findByTenantSlugAndName(fixture.slug(), "AUDITOR").ifPresent(r -> {
            throw new AssertionError("AUDITOR role should be deleted for this test");
        });

        provisioning.ensureDefaultRoles(tenantId);

        UserResponse auditor = createUser(fixture.adminToken(), fixture.slug(), "backfilled", "AUDITOR");
        assertNotNull(auditor.id());
        UserResponse officer = createUser(fixture.adminToken(), fixture.slug(), "officer", "OFFICER");
        assertNotNull(officer.id());
    }

    private record Fixture(String slug, String adminToken, UUID adminId) {
    }

    private Fixture registerTenant() {
        String slug = uniqueSlug("roles");
        String email = "admin@" + slug + ".test";
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
        return new Fixture(slug, response.getBody().accessToken(), response.getBody().user().id());
    }

    private UserResponse createUser(String adminToken, String slug, String who, String role) {
        ResponseEntity<UserResponse> response = post(
                "/api/v1/users",
                adminToken,
                Map.of(
                        "email", who + "@" + slug + ".test",
                        "password", "correct-horse-123",
                        "roleNames", List.of(role)),
                UserResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private String login(String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", "correct-horse-123"),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().accessToken();
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private ResponseEntity<String> post(String url, String token, Object body) {
        return post(url, token, body, String.class);
    }
}
