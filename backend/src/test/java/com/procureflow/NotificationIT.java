package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
 * Event-driven inbox: submitting and deciding a request notifies the
 * requester after commit, reads are personal, failed business work notifies
 * nothing, and tenants never see each other's rows.
 */
class NotificationIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void submitNotifiesRequester() {
        Fixture admin = provision(uniqueSlug("acme"));
        submitted(admin.token(), "Laptops");

        List<Map<String, Object>> inbox = inbox(admin.token());
        assertEquals(1, inbox.size());
        assertEquals("REQUEST_SUBMITTED", inbox.get(0).get("type"));
        assertTrue(inbox.get(0).get("title").toString().contains("Laptops"));
        assertEquals(Boolean.FALSE, inbox.get(0).get("read"));
    }

    @Test
    void approveAndRejectNotifyRequester() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID approved = submitted(admin.token(), "Laptops");
        UUID rejected = submitted(admin.token(), "Monitors");

        decide(admin.token(), approved, "APPROVED");
        decide(admin.token(), rejected, "REJECTED", "Over budget");

        List<Map<String, Object>> inbox = inbox(admin.token());
        assertEquals(4, inbox.size());
        assertEquals("REQUEST_REJECTED", inbox.get(0).get("type"));
        assertTrue(inbox.get(0).get("body").toString().contains("Over budget"));
        assertEquals("REQUEST_APPROVED", inbox.get(1).get("type"));
    }

    @Test
    void inboxIsPersonal() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        submitted(worker.token(), "Mouse");

        assertEquals(1, inbox(worker.token()).size());
        assertTrue(inbox(admin.token()).isEmpty());

        UUID foreign = UUID.fromString(
                inbox(worker.token()).get(0).get("id").toString());
        ResponseEntity<String> forbidden = rest.exchange(
                "/api/v1/notifications/" + foreign + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(bearer(admin.token())),
                String.class);
        // Admins may read for support; a stranger member may not touch it.
        assertEquals(HttpStatus.OK, forbidden.getStatusCode());

        Fixture outsider = join(admin.slug());
        ResponseEntity<String> denied = rest.exchange(
                "/api/v1/notifications/" + foreign + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(bearer(outsider.token())),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    }

    @Test
    void markRead() {
        Fixture admin = provision(uniqueSlug("acme"));
        submitted(admin.token(), "Laptops");
        UUID id = UUID.fromString(inbox(admin.token()).get(0).get("id").toString());

        ResponseEntity<Map> marked = rest.exchange(
                "/api/v1/notifications/" + id + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(bearer(admin.token())),
                Map.class);
        assertEquals(HttpStatus.OK, marked.getStatusCode());
        assertNotNull(marked.getBody());
        assertEquals(Boolean.TRUE, marked.getBody().get("read"));
    }

    @Test
    void failedApprovalNotifiesNothingNew() {
        Fixture admin = provision(uniqueSlug("acme"));
        createBudget(admin.token(), 100000L);
        UUID request = submitted(admin.token(), "Laptops");

        assertEquals(HttpStatus.CONFLICT, decide(admin.token(), request, "APPROVED").getStatusCode());

        List<Map<String, Object>> rows = inbox(admin.token());
        assertEquals(1, rows.size());
        assertEquals("REQUEST_SUBMITTED", rows.get(0).get("type"));
    }

    @Test
    void tenantsNeverShareAnInbox() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        submitted(tenantA.token(), "Laptops");

        assertTrue(inbox(tenantB.token()).isEmpty());

        UUID foreign = UUID.fromString(
                inbox(tenantA.token()).get(0).get("id").toString());
        assertEquals(
                HttpStatus.NOT_FOUND,
                rest.exchange(
                                "/api/v1/notifications/" + foreign + "/read",
                                HttpMethod.PATCH,
                                new HttpEntity<>(bearer(tenantB.token())),
                                String.class)
                        .getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> inbox(String token) {
        ResponseEntity<Map> response = get("/api/v1/notifications", token, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inboxPaginatesNewestFirst() {
        Fixture admin = provision(uniqueSlug("acme"));
        submitted(admin.token(), "Laptops");
        submitted(admin.token(), "Monitors");
        submitted(admin.token(), "Keyboards");

        ResponseEntity<Map> first = get("/api/v1/notifications?page=0&size=2", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertNotNull(first.getBody());
        assertEquals(2, ((List<?>) first.getBody().get("content")).size());
        assertEquals(3L, ((Number) first.getBody().get("totalElements")).longValue());
        assertEquals(2L, ((Number) first.getBody().get("totalPages")).longValue());

        ResponseEntity<Map> second = get("/api/v1/notifications?page=1&size=2", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertNotNull(second.getBody());
        assertEquals(1, ((List<?>) second.getBody().get("content")).size());

        assertEquals(
                HttpStatus.BAD_REQUEST,
                get("/api/v1/notifications?size=101", admin.token(), String.class).getStatusCode());
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

    private Fixture join(String slug) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", "worker-" + UUID.randomUUID().toString().substring(0, 8) + "@" + slug + ".test",
                        "password", "correct-horse-123",
                        "tenantSlug", slug),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return new Fixture(slug, response.getBody().accessToken());
    }

    private UUID submitted(String token, String title) {
        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/purchase-requests",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "title", title,
                                "items", List.of(Map.of(
                                        "description", "ThinkPad",
                                        "quantity", 2,
                                        "unitPriceMinor", 89900))),
                        withIdempotency(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        UUID id = UUID.fromString(created.getBody().get("id").toString());
        ResponseEntity<Map> submitted =
                post("/api/v1/purchase-requests/" + id + "/submit", token, Map.of(), Map.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        return id;
    }

    private ResponseEntity<Map> decide(String token, UUID requestId, String decision) {
        return decide(token, requestId, decision, null);
    }

    private ResponseEntity<Map> decide(String token, UUID requestId, String decision, String comment) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("requestId", requestId);
        body.put("decision", decision);
        if (comment != null) {
            body.put("comment", comment);
        }
        return rest.exchange(
                "/api/v1/approvals/decisions", HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
    }

    private void createBudget(String token, long amount) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/budgets",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Ops",
                                "period", YearMonth.now(ZoneOffset.UTC).toString(),
                                "amountMinor", amount,
                                "currency", "MAD"),
                        bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private org.springframework.http.HttpHeaders withIdempotency(String token) {
        org.springframework.http.HttpHeaders headers = bearer(token);
        headers.set("Idempotency-Key", "key-" + UUID.randomUUID());
        return headers;
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private record Fixture(String slug, String token) {
    }
}
