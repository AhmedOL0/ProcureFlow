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
 * Supplier orders: born only from approved requests with snapshotted lines,
 * a real two-thread create race (one order wins), send flips the request to
 * ORDERED, receipt accumulates to RECEIVED, the supplier guard refuses
 * deletes, and tenants never see each other's orders.
 */
class OrderIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void createFromApprovedOnly() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID draft = createRequest(admin.token());

        assertEquals(HttpStatus.CONFLICT, createOrder(admin.token(), draft, supplier, Map.class).getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                createOrder(admin.token(), UUID.randomUUID(), supplier, Map.class).getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                createOrder(admin.token(), draft, UUID.randomUUID(), Map.class).getStatusCode());
    }

    @Test
    void snapshotMirrorsRequestLines() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID request = approved(admin.token());

        ResponseEntity<Map> created = createOrder(admin.token(), request, supplier, Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals("DRAFT", created.getBody().get("status"));
        assertEquals(179800L, ((Number) created.getBody().get("totalMinor")).longValue());
        assertNotNull(created.getBody().get("lines"));
        assertEquals(1, ((List<?>) created.getBody().get("lines")).size());
    }

    @Test
    void concurrentCreatesYieldOneOrder() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID request = approved(admin.token());
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> attempt = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return createOrder(admin.token(), request, supplier, Map.class);
            };
            Future<ResponseEntity<Map>> first = pool.submit(attempt);
            Future<ResponseEntity<Map>> second = pool.submit(attempt);
            gate.countDown();

            var statusA = first.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = second.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(
                    (statusA == HttpStatus.CREATED && statusB == HttpStatus.CONFLICT)
                            || (statusA == HttpStatus.CONFLICT && statusB == HttpStatus.CREATED));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sendFlipsRequestToOrdered() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID order = ordered(admin.token(), supplier, approved(admin.token()));

        assertEquals(HttpStatus.CONFLICT, post("/api/v1/purchase-orders/" + order + "/send", admin.token())
                .getStatusCode());
    }

    @Test
    void receiptAccumulatesToReceived() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID request = approved(admin.token());
        UUID order = createId(admin.token(), request, supplier);

        UUID line = lineId(admin.token(), order, 0);
        assertEquals("SENT", transition(admin.token(), order, "send").get("status"));

        ResponseEntity<Map> partial = receive(admin.token(), order, List.of(Map.of("itemId", line, "quantity", 1)));
        assertEquals(HttpStatus.OK, partial.getStatusCode());
        assertNotNull(partial.getBody());
        assertEquals("PARTIALLY_RECEIVED", partial.getBody().get("status"));

        assertEquals(
                HttpStatus.CONFLICT,
                receive(admin.token(), order, List.of(Map.of("itemId", line, "quantity", 2))).getStatusCode());

        ResponseEntity<Map> full = receive(admin.token(), order, List.of(Map.of("itemId", line, "quantity", 1)));
        assertEquals(HttpStatus.OK, full.getStatusCode());
        assertEquals("RECEIVED", full.getBody().get("status"));

        assertEquals("CLOSED", transition(admin.token(), order, "close").get("status"));
    }

    @Test
    void lifecycleGuardsHold() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID draft = createId(admin.token(), approved(admin.token()), supplier);

        assertEquals("CONFLICT", transition(admin.token(), draft, "close").get("status"));
        UUID draftLine = lineId(admin.token(), draft, 0);
        assertEquals(
                HttpStatus.CONFLICT,
                receive(admin.token(), draft, List.of(Map.of("itemId", draftLine, "quantity", 1))).getStatusCode());
        assertEquals("CANCELLED", transition(admin.token(), draft, "cancel").get("status"));

        UUID sent = createId(admin.token(), approved(admin.token()), supplier);
        transition(admin.token(), sent, "send");
        assertEquals("CANCELLED", transition(admin.token(), sent, "cancel").get("status"));
    }

    @Test
    void referencedSupplierCannotBeDeleted() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID used = createSupplier(admin.token(), "Used Parts");
        UUID free = createSupplier(admin.token(), "Free Parts");
        createId(admin.token(), approved(admin.token()), used);

        assertEquals(HttpStatus.CONFLICT, deleteSupplier(admin.token(), used).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, deleteSupplier(admin.token(), free).getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void concurrentReceiptsAdmitNoOverReceive() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID order = ordered(admin.token(), supplier, approved(admin.token()));
        UUID line = lineId(admin.token(), order, 0);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> attempt = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return receive(admin.token(), order, List.of(Map.of("itemId", line, "quantity", 2)));
            };
            Future<ResponseEntity<Map>> attemptA = pool.submit(attempt);
            Future<ResponseEntity<Map>> attemptB = pool.submit(attempt);
            gate.countDown();

            var statusA = attemptA.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = attemptB.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(
                    (statusA == HttpStatus.OK && statusB == HttpStatus.CONFLICT)
                            || (statusA == HttpStatus.CONFLICT && statusB == HttpStatus.OK));

            ResponseEntity<Map> fetched = get("/api/v1/purchase-orders/" + order, admin.token(), Map.class);
            assertEquals(HttpStatus.OK, fetched.getStatusCode());
            assertNotNull(fetched.getBody());
            List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.getBody().get("lines");
            assertNotNull(lines);
            assertEquals(2, ((Number) lines.get(0).get("receivedQty")).intValue());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ordersAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID supplier = createSupplier(tenantA.token(), "Acme Parts");
        UUID request = approved(tenantA.token());
        UUID order = createId(tenantA.token(), request, supplier);

        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/purchase-orders/" + order, tenantB.token(), String.class).getStatusCode());
        ResponseEntity<Map> list = get("/api/v1/purchase-orders", tenantB.token(), Map.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(((List<?>) list.getBody().get("content")).isEmpty());

        assertEquals(
                HttpStatus.NOT_FOUND,
                createOrder(tenantB.token(), request, supplier, Map.class).getStatusCode());
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

    private UUID createRequest(String token) {
        ResponseEntity<Map> response = rest.exchange(
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
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID approved(String token) {
        UUID id = createRequest(token);
        ResponseEntity<Map> submitted =
                post("/api/v1/purchase-requests/" + id + "/submit", token, Map.of(), Map.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        ResponseEntity<Map> decided = rest.exchange(
                "/api/v1/approvals/decisions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", id, "decision", "APPROVED"), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, decided.getStatusCode());
        return id;
    }

    private UUID createSupplier(String token, String name) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/suppliers",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private <T> ResponseEntity<T> createOrder(String token, UUID request, UUID supplier, Class<T> type) {
        return rest.exchange(
                "/api/v1/purchase-orders",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", request, "supplierId", supplier), bearer(token)),
                type);
    }

    private UUID createId(String token, UUID request, UUID supplier) {
        ResponseEntity<Map> response = createOrder(token, request, supplier, Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID ordered(String token, UUID supplier, UUID request) {
        UUID order = createId(token, request, supplier);
        assertEquals("SENT", transition(token, order, "send").get("status"));
        ResponseEntity<Map> fetched = get("/api/v1/purchase-requests/" + request, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        assertEquals("ORDERED", fetched.getBody().get("status"));
        return order;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> transition(String token, UUID order, String action) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/purchase-orders/" + order + "/" + action,
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), bearer(token)),
                Map.class);
        if (response.getStatusCode() == HttpStatus.CONFLICT) {
            return Map.of("status", "CONFLICT");
        }
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private UUID lineId(String token, UUID order, int index) {
        ResponseEntity<Map> fetched = get("/api/v1/purchase-orders/" + order, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.getBody().get("lines");
        assertNotNull(lines);
        return UUID.fromString(lines.get(index).get("id").toString());
    }

    private ResponseEntity<Map> receive(String token, UUID order, Object lines) {
        return rest.exchange(
                "/api/v1/purchase-orders/" + order + "/receive",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("lines", lines), bearer(token)),
                Map.class);
    }

    private ResponseEntity<String> deleteSupplier(String token, UUID supplier) {
        return rest.exchange(
                "/api/v1/suppliers/" + supplier, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), String.class);
    }

    private org.springframework.http.HttpHeaders withIdempotency(String token) {
        org.springframework.http.HttpHeaders headers = bearer(token);
        headers.set("Idempotency-Key", "key-" + UUID.randomUUID());
        return headers;
    }

    private ResponseEntity<String> post(String url, String token) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of(), bearer(token)), String.class);
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private record Fixture(String slug, String token) {
    }
}
