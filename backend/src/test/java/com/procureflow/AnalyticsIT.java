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
 * Read-only KPIs: spend totals with category and monthly breakdowns,
 * supplier scorecard averages, approval queue depth and lead times.
 * Members may read; tenants never see each other's numbers.
 */
class AnalyticsIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void spendAggregatesAcrossTheFlow() {
        Fixture admin = provision(uniqueSlug("acme"));
        paidFlow(admin.token());

        ResponseEntity<Map> spend = get("/api/v1/analytics/spend", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, spend.getStatusCode());
        assertNotNull(spend.getBody());
        assertEquals(181300L, number(spend.getBody(), "requestedMinor"));
        assertEquals(181300L, number(spend.getBody(), "orderedMinor"));
        assertEquals(181300L, number(spend.getBody(), "invoicedMinor"));
        assertEquals(181300L, number(spend.getBody(), "paidMinor"));

        assertCategory(spend.getBody(), "Hardware", 179800L);
        assertCategory(spend.getBody(), "Accessories", 1500L);

        String month = YearMonth.now(ZoneOffset.UTC).toString();
        assertMonth(spend.getBody(), month, 181300L, 181300L, 181300L);
    }

    @Test
    void spendPeriodFilter() {
        Fixture admin = provision(uniqueSlug("acme"));
        paidFlow(admin.token());
        String month = YearMonth.now(ZoneOffset.UTC).toString();

        ResponseEntity<Map> filtered = get("/api/v1/analytics/spend?period=" + month, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, filtered.getStatusCode());
        assertNotNull(filtered.getBody());
        assertEquals(181300L, number(filtered.getBody(), "orderedMinor"));

        ResponseEntity<Map> empty = get("/api/v1/analytics/spend?period=2000-01", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, empty.getStatusCode());
        assertNotNull(empty.getBody());
        assertEquals(0L, number(empty.getBody(), "orderedMinor"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                get("/api/v1/analytics/spend?period=last-year", admin.token(), String.class).getStatusCode());
    }

    @Test
    void supplierScorecardAverages() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = supplier(admin.token(), "Acme Parts");
        score(admin.token(), supplier, "2026-01", 80, 70);
        score(admin.token(), supplier, "2026-02", 100, 90);

        ResponseEntity<List> response = get("/api/v1/analytics/suppliers", admin.token(), List.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) response.getBody().get(0);
        assertEquals("Acme Parts", row.get("supplierName"));
        assertEquals(2, ((Number) row.get("periods")).intValue());
        assertEquals(90.0, ((Number) row.get("avgOnTime")).doubleValue());
        assertEquals(80.0, ((Number) row.get("avgQuality")).doubleValue());
    }

    @Test
    void approvalQueueAndLeadTimes() {
        Fixture admin = provision(uniqueSlug("acme"));
        paidFlow(admin.token());
        submitted(admin.token());

        ResponseEntity<Map> kpis = get("/api/v1/analytics/approvals", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, kpis.getStatusCode());
        assertNotNull(kpis.getBody());
        assertEquals(1L, number(kpis.getBody(), "pending"));
        assertEquals(1L, number(kpis.getBody(), "decided"));
        assertNotNull(kpis.getBody().get("avgLeadHours"));
        assertNotNull(kpis.getBody().get("maxLeadHours"));
    }

    @Test
    void membersReadButTenantsAreIsolated() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        Fixture tenantB = provision(uniqueSlug("globex"));
        paidFlow(admin.token());

        assertEquals(HttpStatus.OK, get("/api/v1/analytics/spend", worker.token(), Map.class).getStatusCode());

        ResponseEntity<Map> foreign = get("/api/v1/analytics/spend", tenantB.token(), Map.class);
        assertEquals(HttpStatus.OK, foreign.getStatusCode());
        assertNotNull(foreign.getBody());
        assertEquals(0L, number(foreign.getBody(), "orderedMinor"));

        ResponseEntity<List> scores = get("/api/v1/analytics/suppliers", tenantB.token(), List.class);
        assertEquals(HttpStatus.OK, scores.getStatusCode());
        assertNotNull(scores.getBody());
        assertTrue(scores.getBody().isEmpty());
    }

    private long number(Map<String, Object> body, String key) {
        assertNotNull(body.get(key));
        return ((Number) body.get(key)).longValue();
    }

    @SuppressWarnings("unchecked")
    private void assertCategory(Map<String, Object> body, String name, long amount) {
        List<Map<String, Object>> categories = (List<Map<String, Object>>) body.get("byCategory");
        assertNotNull(categories);
        Map<String, Object> found = categories.stream()
                .filter(c -> name.equals(c.get("category")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing category " + name));
        assertEquals(amount, ((Number) found.get("amountMinor")).longValue());
    }

    @SuppressWarnings("unchecked")
    private void assertMonth(Map<String, Object> body, String period, long ordered, long invoiced, long paid) {
        List<Map<String, Object>> months = (List<Map<String, Object>>) body.get("byPeriod");
        assertNotNull(months);
        Map<String, Object> found = months.stream()
                .filter(m -> period.equals(m.get("period")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing period " + period));
        assertEquals(ordered, ((Number) found.get("orderedMinor")).longValue());
        assertEquals(invoiced, ((Number) found.get("invoicedMinor")).longValue());
        assertEquals(paid, ((Number) found.get("paidMinor")).longValue());
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

    private void score(String token, UUID supplier, String period, int onTime, int quality) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/suppliers/" + supplier + "/performances",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("period", period, "onTimeRate", onTime, "qualityScore", quality), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private UUID submitted(String token) {
        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/purchase-requests",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "title", "Laptops",
                                "items", List.of(
                                        Map.of(
                                                "description", "ThinkPad",
                                                "category", "Hardware",
                                                "quantity", 2,
                                                "unitPriceMinor", 89900),
                                        Map.of(
                                                "description", "Cable",
                                                "category", "Accessories",
                                                "quantity", 1,
                                                "unitPriceMinor", 1500))),
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

    private void paidFlow(String token) {
        UUID request = submitted(token);
        assertEquals(
                HttpStatus.CREATED,
                rest.exchange(
                                "/api/v1/approvals/decisions",
                                HttpMethod.POST,
                                new HttpEntity<>(Map.of("requestId", request, "decision", "APPROVED"), bearer(token)),
                                Map.class)
                        .getStatusCode());
        UUID supplier = supplier(token, "Acme Parts");
        ResponseEntity<Map> order = rest.exchange(
                "/api/v1/purchase-orders",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", request, "supplierId", supplier), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, order.getStatusCode());
        assertNotNull(order.getBody());
        UUID orderId = UUID.fromString(order.getBody().get("id").toString());
        assertEquals(
                HttpStatus.OK,
                post("/api/v1/purchase-orders/" + orderId + "/send", token, Map.of(), Map.class).getStatusCode());

        ResponseEntity<Map> fetched = get("/api/v1/purchase-orders/" + orderId, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.getBody().get("lines");
        assertNotNull(lines);
        List<Map<String, Object>> invoiceLines = lines.stream()
                .map(l -> Map.<String, Object>of("orderItemId", l.get("id"), "quantity", l.get("quantity")))
                .toList();
        ResponseEntity<Map> invoice = rest.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", orderId, "number", "INV-1", "lines", invoiceLines), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, invoice.getStatusCode());
        assertNotNull(invoice.getBody());
        UUID invoiceId = UUID.fromString(invoice.getBody().get("id").toString());
        assertEquals(
                HttpStatus.OK,
                rest.exchange(
                                "/api/v1/invoices/" + invoiceId + "/payments",
                                HttpMethod.POST,
                                new HttpEntity<>(Map.of("amountMinor", 181300), bearer(token)),
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

    private record Fixture(String slug, String token) {
    }
}
