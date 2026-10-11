package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Append-only audit trail: decisions, order transitions, invoices and
 * payments land with actor, action and before/after payloads; failed work
 * writes nothing; reads are admin-only and tenant-scoped.
 */
class AuditIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void decideWritesAuditRow() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = approved(admin.token());

        List<Map<String, Object>> rows = trail(admin.token(), "purchase_request", request);
        assertEquals(1, rows.size());
        assertEquals("REQUEST_DECIDED", rows.get(0).get("action"));
        assertEquals(admin.userId().toString(), rows.get(0).get("actorId"));
        assertTrue(rows.get(0).get("beforeJson").toString().contains("SUBMITTED"));
        assertTrue(rows.get(0).get("afterJson").toString().contains("APPROVED"));
    }

    @Test
    void orderLifecycleWritesRows() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = supplier(admin.token(), "Acme Parts");
        UUID order = order(admin.token(), approved(admin.token()), supplier);
        post("/api/v1/purchase-orders/" + order + "/send", admin.token(), Map.of(), Map.class);

        List<Map<String, Object>> rows = trail(admin.token(), "purchase_order", order);
        assertEquals(2, rows.size());
        assertEquals("ORDER_SENT", rows.get(0).get("action"));
        assertTrue(rows.get(0).get("beforeJson").toString().contains("DRAFT"));
        assertEquals("ORDER_CREATED", rows.get(1).get("action"));
    }

    @Test
    void invoiceAndPaymentWriteRows() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = supplier(admin.token(), "Acme Parts");
        UUID order = sent(admin.token(), approved(admin.token()), supplier);
        UUID line = orderLine(admin.token(), order);
        UUID invoice = invoice(admin.token(), order, "INV-001", line, 2);
        pay(admin.token(), invoice, 179800L);

        List<Map<String, Object>> rows = trail(admin.token(), "invoice", invoice);
        assertEquals(2, rows.size());
        assertEquals("INVOICE_PAID", rows.get(0).get("action"));
        assertTrue(rows.get(0).get("afterJson").toString().contains("PAID"));
        assertEquals("INVOICE_CREATED", rows.get(1).get("action"));
    }

    @Test
    void failedWorkWritesNothing() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin.token());
        assertEquals(
                HttpStatus.CREATED, decide(admin.token(), request, "APPROVED").getStatusCode());
        assertEquals(
                HttpStatus.CONFLICT, decide(admin.token(), request, "APPROVED").getStatusCode());

        assertEquals(1, trail(admin.token(), "purchase_request", request).size());
    }

    @Test
    void readsAreAdminOnlyAndTenantScoped() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        Fixture tenantB = provision(uniqueSlug("globex"));
        approved(admin.token());

        assertEquals(
                HttpStatus.FORBIDDEN,
                get("/api/v1/audit-events", worker.token(), String.class).getStatusCode());

        ResponseEntity<Map> own = trailRaw(admin.token(), null, null);
        assertEquals(HttpStatus.OK, own.getStatusCode());
        assertNotNull(own.getBody());
        assertTrue(((List<?>) own.getBody().get("content")).size() >= 1);
        assertTrue(((Number) own.getBody().get("totalElements")).longValue() >= 1);

        ResponseEntity<Map> foreign = trailRaw(tenantB.token(), null, null);
        assertEquals(HttpStatus.OK, foreign.getStatusCode());
        assertNotNull(foreign.getBody());
        assertTrue(((List<?>) foreign.getBody().get("content")).isEmpty());
        assertEquals(0L, ((Number) foreign.getBody().get("totalElements")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void trailPaginatesAndValidatesBounds() {
        Fixture admin = provision(uniqueSlug("acme"));
        approved(admin.token());
        approved(admin.token());

        ResponseEntity<Map> page0 = get("/api/v1/audit-events?size=1", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, page0.getStatusCode());
        assertNotNull(page0.getBody());
        assertEquals(1, ((List<?>) page0.getBody().get("content")).size());
        assertTrue(((Number) page0.getBody().get("totalElements")).longValue() >= 2);
        assertTrue(((Number) page0.getBody().get("totalPages")).longValue() >= 2);
        assertEquals(0, ((Number) page0.getBody().get("page")).intValue());

        ResponseEntity<Map> page1 = get("/api/v1/audit-events?size=1&page=1", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, page1.getStatusCode());
        List<Map<String, Object>> rows0 = (List<Map<String, Object>>) page0.getBody().get("content");
        List<Map<String, Object>> rows1 = (List<Map<String, Object>>) page1.getBody().get("content");
        assertEquals(1, rows1.size());
        assertNotEquals(rows0.get(0).get("id"), rows1.get(0).get("id"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                get("/api/v1/audit-events?page=-1", admin.token(), String.class).getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                get("/api/v1/audit-events?size=0", admin.token(), String.class).getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                get("/api/v1/audit-events?size=101", admin.token(), String.class).getStatusCode());
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
        assertNotNull(response.getBody().user());
        return new Fixture(slug, response.getBody().accessToken(), response.getBody().user().id());
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
        return new Fixture(slug, response.getBody().accessToken(), null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> trail(String token, String entityType, UUID entityId) {
        ResponseEntity<Map> response = trailRaw(token, entityType, entityId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    private ResponseEntity<Map> trailRaw(String token, String entityType, UUID entityId) {
        String url = "/api/v1/audit-events";
        if (entityType != null) {
            url += "?entityType=" + entityType;
            if (entityId != null) {
                url += "&entityId=" + entityId;
            }
        }
        return get(url, token, Map.class);
    }

    private UUID submitted(String token) {
        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/purchase-requests",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "title", "Laptops",
                                "items", List.of(Map.of(
                                        "description", "ThinkPad",
                                        "quantity", 2,
                                        "unitPriceMinor", 89900))),
                        withIdempotency(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        UUID id = UUID.fromString(created.getBody().get("id").toString());
        assertEquals(
                HttpStatus.OK,
                post("/api/v1/purchase-requests/" + id + "/submit", token, Map.of(), Map.class).getStatusCode());
        return id;
    }

    private UUID approved(String token) {
        UUID id = submitted(token);
        assertEquals(HttpStatus.CREATED, decide(token, id, "APPROVED").getStatusCode());
        return id;
    }

    private ResponseEntity<Map> decide(String token, UUID request, String decision) {
        return rest.exchange(
                "/api/v1/approvals/decisions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", request, "decision", decision), bearer(token)),
                Map.class);
    }

    private UUID supplier(String token, String name) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/suppliers",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID order(String token, UUID request, UUID supplier) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/purchase-orders",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", request, "supplierId", supplier), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID sent(String token, UUID request, UUID supplier) {
        UUID order = order(token, request, supplier);
        assertEquals(
                HttpStatus.OK,
                post("/api/v1/purchase-orders/" + order + "/send", token, Map.of(), Map.class).getStatusCode());
        return order;
    }

    @SuppressWarnings("unchecked")
    private UUID orderLine(String token, UUID order) {
        ResponseEntity<Map> fetched = get("/api/v1/purchase-orders/" + order, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.getBody().get("lines");
        assertNotNull(lines);
        return UUID.fromString(lines.get(0).get("id").toString());
    }

    private UUID invoice(String token, UUID order, String number, UUID line, int qty) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "orderId", order,
                                "number", number,
                                "lines", List.of(Map.of("orderItemId", line, "quantity", qty))),
                        bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void pay(String token, UUID invoice, long amount) {
        assertEquals(
                HttpStatus.OK,
                rest.exchange(
                                "/api/v1/invoices/" + invoice + "/payments",
                                HttpMethod.POST,
                                new HttpEntity<>(Map.of("amountMinor", amount), bearer(token)),
                                Map.class)
                        .getStatusCode());
    }

    private org.springframework.http.HttpHeaders withIdempotency(String token) {
        org.springframework.http.HttpHeaders headers = bearer(token);
        headers.set("Idempotency-Key", "key-" + UUID.randomUUID());
        return headers;
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private record Fixture(String slug, String token, UUID userId) {
    }
}
