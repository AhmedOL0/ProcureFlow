package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Supplier invoices: booked only on sent orders, a 3-way-match-lite that
 * refuses over-invoicing, UNPAID → PARTIAL → PAID payments without overpay,
 * and cross-tenant negatives throughout.
 */
class InvoiceIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void invoiceFromSentOrderCarriesMatch() {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow flow = sentOrder(admin.token());

        ResponseEntity<Map> created =
                invoice(admin.token(), flow.order(), "INV-001", List.of(Map.of("orderItemId", flow.line(), "quantity", 2)));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals("UNPAID", created.getBody().get("status"));
        assertEquals(179800L, ((Number) created.getBody().get("totalMinor")).longValue());

        assertMatch(created.getBody(), 2, 0, 2);
    }

    @Test
    void draftOrderCannotBeInvoiced() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = supplier(admin.token(), "Acme Parts");
        UUID request = approved(admin.token());
        UUID order = order(admin.token(), request, supplier);

        assertEquals(
                HttpStatus.CONFLICT,
                invoice(admin.token(), order, "INV-001", List.of(Map.of("orderItemId", line(admin.token(), order), "quantity", 1)))
                        .getStatusCode());
    }

    @Test
    void overInvoicingRejected() {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow flow = sentOrder(admin.token());

        assertEquals(
                HttpStatus.CREATED,
                invoice(admin.token(), flow.order(), "INV-001", List.of(Map.of("orderItemId", flow.line(), "quantity", 1)))
                        .getStatusCode());
        assertEquals(
                HttpStatus.CREATED,
                invoice(admin.token(), flow.order(), "INV-002", List.of(Map.of("orderItemId", flow.line(), "quantity", 1)))
                        .getStatusCode());
        assertEquals(
                HttpStatus.CONFLICT,
                invoice(admin.token(), flow.order(), "INV-003", List.of(Map.of("orderItemId", flow.line(), "quantity", 1)))
                        .getStatusCode());
    }

    @Test
    void paymentsWalkToPaidWithoutOverpay() {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow flow = sentOrder(admin.token());
        UUID invoice = invoiceId(
                admin.token(), flow.order(), "INV-001", List.of(Map.of("orderItemId", flow.line(), "quantity", 2)));

        assertEquals("PARTIAL", pay(admin.token(), invoice, 89800L).get("status"));
        assertEquals("PAID", pay(admin.token(), invoice, 90000L).get("status"));

        ResponseEntity<Map> overpaid = rest.exchange(
                "/api/v1/invoices/" + invoice + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amountMinor", 1), bearer(admin.token())),
                Map.class);
        assertEquals(HttpStatus.CONFLICT, overpaid.getStatusCode());
    }

    @Test
    void duplicateNumbersAndForeignLinesRejected() {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow first = sentOrder(admin.token());
        Flow second = sentOrder(admin.token());

        invoiceId(admin.token(), first.order(), "INV-001", List.of(Map.of("orderItemId", first.line(), "quantity", 1)));
        assertEquals(
                HttpStatus.CONFLICT,
                invoice(admin.token(), second.order(), "INV-001", List.of(Map.of("orderItemId", second.line(), "quantity", 1)))
                        .getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                invoice(admin.token(), first.order(), "INV-002", List.of(Map.of("orderItemId", second.line(), "quantity", 1)))
                        .getStatusCode());
    }

    @Test
    void matchReflectsReceipt() {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow flow = sentOrder(admin.token());
        receive(admin.token(), flow.order(), flow.line(), 2);
        UUID invoice = invoiceId(
                admin.token(), flow.order(), "INV-001", List.of(Map.of("orderItemId", flow.line(), "quantity", 2)));

        ResponseEntity<Map> fetched = get("/api/v1/invoices/" + invoice, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        assertMatch(fetched.getBody(), 2, 2, 2);
    }

    @Test
    void concurrentInvoiceBookingsAdmitNoOverInvoice() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow flow = sentOrder(admin.token());
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> first = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return invoice(
                        admin.token(),
                        flow.order(),
                        "INV-R1",
                        List.of(Map.of("orderItemId", flow.line(), "quantity", 2)));
            };
            Callable<ResponseEntity<Map>> second = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return invoice(
                        admin.token(),
                        flow.order(),
                        "INV-R2",
                        List.of(Map.of("orderItemId", flow.line(), "quantity", 2)));
            };
            Future<ResponseEntity<Map>> attemptA = pool.submit(first);
            Future<ResponseEntity<Map>> attemptB = pool.submit(second);
            gate.countDown();

            var statusA = attemptA.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = attemptB.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(
                    (statusA == HttpStatus.CREATED && statusB == HttpStatus.CONFLICT)
                            || (statusA == HttpStatus.CONFLICT && statusB == HttpStatus.CREATED));

            ResponseEntity<List> list = get("/api/v1/invoices", admin.token(), List.class);
            assertEquals(HttpStatus.OK, list.getStatusCode());
            assertNotNull(list.getBody());
            assertEquals(1, list.getBody().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentPaymentsAdmitNoOverpay() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        Flow flow = sentOrder(admin.token());
        UUID invoice = invoiceId(
                admin.token(), flow.order(), "INV-001", List.of(Map.of("orderItemId", flow.line(), "quantity", 2)));
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> attempt = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return rest.exchange(
                        "/api/v1/invoices/" + invoice + "/payments",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("amountMinor", 179800L), bearer(admin.token())),
                        Map.class);
            };
            Future<ResponseEntity<Map>> attemptA = pool.submit(attempt);
            Future<ResponseEntity<Map>> attemptB = pool.submit(attempt);
            gate.countDown();

            var statusA = attemptA.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = attemptB.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(
                    (statusA == HttpStatus.OK && statusB == HttpStatus.CONFLICT)
                            || (statusA == HttpStatus.CONFLICT && statusB == HttpStatus.OK));

            ResponseEntity<Map> fetched = get("/api/v1/invoices/" + invoice, admin.token(), Map.class);
            assertEquals(HttpStatus.OK, fetched.getStatusCode());
            assertNotNull(fetched.getBody());
            assertEquals("PAID", fetched.getBody().get("status"));
            assertEquals(179800L, ((Number) fetched.getBody().get("paidMinor")).longValue());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invoicesAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        Flow flow = sentOrder(tenantA.token());
        UUID invoice =
                invoiceId(tenantA.token(), flow.order(), "INV-001", List.of(Map.of("orderItemId", flow.line(), "quantity", 1)));

        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/invoices/" + invoice, tenantB.token(), String.class).getStatusCode());
        ResponseEntity<List> list = get("/api/v1/invoices", tenantB.token(), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(list.getBody().isEmpty());

        UUID ownOrder = sentOrder(tenantB.token()).order();
        assertEquals(
                HttpStatus.NOT_FOUND,
                invoice(tenantB.token(), flow.order(), "INV-9", List.of(Map.of("orderItemId", flow.line(), "quantity", 1)))
                        .getStatusCode());
        assertNotNull(ownOrder);
    }

    @SuppressWarnings("unchecked")
    private void assertMatch(Map<String, Object> body, int ordered, int received, long invoiced) {
        List<Map<String, Object>> match = (List<Map<String, Object>>) body.get("match");
        assertNotNull(match);
        assertEquals(1, match.size());
        assertEquals(ordered, ((Number) match.get(0).get("orderedQty")).intValue());
        assertEquals(received, ((Number) match.get(0).get("receivedQty")).intValue());
        assertEquals(invoiced, ((Number) match.get(0).get("invoicedQty")).longValue());
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

    private UUID approved(String token) {
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
        assertEquals(
                HttpStatus.CREATED,
                rest.exchange(
                                "/api/v1/approvals/decisions",
                                HttpMethod.POST,
                                new HttpEntity<>(Map.of("requestId", id, "decision", "APPROVED"), bearer(token)),
                                Map.class)
                        .getStatusCode());
        return id;
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

    private Flow sentOrder(String token) {
        UUID order = order(token, approved(token), supplier(token, "Acme-" + UUID.randomUUID().toString().substring(0, 6)));
        assertEquals(
                HttpStatus.OK, post("/api/v1/purchase-orders/" + order + "/send", token, Map.of(), Map.class).getStatusCode());
        return new Flow(order, line(token, order));
    }

    @SuppressWarnings("unchecked")
    private UUID line(String token, UUID order) {
        ResponseEntity<Map> fetched = get("/api/v1/purchase-orders/" + order, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.getBody().get("lines");
        assertNotNull(lines);
        return UUID.fromString(lines.get(0).get("id").toString());
    }

    private void receive(String token, UUID order, UUID line, int qty) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/purchase-orders/" + order + "/receive",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("lines", List.of(Map.of("itemId", line, "quantity", qty))), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private ResponseEntity<Map> invoice(String token, UUID order, String number, Object lines) {
        return rest.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", order, "number", number, "lines", lines), bearer(token)),
                Map.class);
    }

    private UUID invoiceId(String token, UUID order, String number, Object lines) {
        ResponseEntity<Map> response = invoice(token, order, number, lines);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private Map<String, Object> pay(String token, UUID invoice, long amount) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/invoices/" + invoice + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amountMinor", amount), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
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

    private record Flow(UUID order, UUID line) {
    }
}
