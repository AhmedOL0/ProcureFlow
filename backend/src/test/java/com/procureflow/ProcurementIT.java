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
 * Purchase request lifecycle: idempotent creation (including a real
 * two-thread race on one key), draft editing rules, submit validation,
 * cancel transitions, supplier linkage and tenant isolation.
 */
class ProcurementIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void createReplaysOnSameKey() {
        Fixture admin = provision(uniqueSlug("acme"));
        String key = "key-" + UUID.randomUUID();

        ResponseEntity<Map> first = create(admin.token(), key, Map.of("title", "Laptops"));
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        ResponseEntity<Map> replay = create(admin.token(), key, Map.of("title", "Laptops"));
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(replay.getBody());
        assertEquals(first.getBody().get("id"), replay.getBody().get("id"));
        assertTrue(replay.getHeaders().containsKey("X-Replay"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        Fixture admin = provision(uniqueSlug("acme"));

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/purchase-requests",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Laptops"), bearer(admin.token())),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void concurrentCreatesWithSameKeyYieldOneRequest() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        String key = "race-" + UUID.randomUUID();
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> attempt = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return create(admin.token(), key, Map.of("title", "Race laptops"));
            };
            Future<ResponseEntity<Map>> first = pool.submit(attempt);
            Future<ResponseEntity<Map>> second = pool.submit(attempt);
            gate.countDown();

            var statusA = first.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = second.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(statusA.is2xxSuccessful() && statusB.is2xxSuccessful());
            assertTrue(
                    (statusA == HttpStatus.CREATED && statusB == HttpStatus.OK)
                            || (statusA == HttpStatus.OK && statusB == HttpStatus.CREATED));

            ResponseEntity<List> list = get("/api/v1/purchase-requests", admin.token(), List.class);
            assertEquals(HttpStatus.OK, list.getStatusCode());
            assertNotNull(list.getBody());
            assertEquals(1, list.getBody().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void fullDraftLifecycle() {
        Fixture admin = provision(uniqueSlug("acme"));

        UUID id = createId(admin.token(), "Laptops", null);

        ResponseEntity<String> emptySubmit = post(
                "/api/v1/purchase-requests/" + id + "/submit", admin.token(), Map.of(), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, emptySubmit.getStatusCode());

        ResponseEntity<Map> item = post(
                "/api/v1/purchase-requests/" + id + "/items",
                admin.token(),
                Map.of("description", "ThinkPad", "quantity", 20, "unitPriceMinor", 89900L),
                Map.class);
        assertEquals(HttpStatus.CREATED, item.getStatusCode());

        ResponseEntity<Map> submitted = post(
                "/api/v1/purchase-requests/" + id + "/submit", admin.token(), Map.of(), Map.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        assertNotNull(submitted.getBody());
        assertEquals("SUBMITTED", submitted.getBody().get("status"));
        assertEquals(1798000L, ((Number) submitted.getBody().get("totalMinor")).longValue());

        ResponseEntity<String> editAfterSubmit = rest.exchange(
                "/api/v1/purchase-requests/" + id,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("title", "Changed"), bearer(admin.token())),
                String.class);
        assertEquals(HttpStatus.CONFLICT, editAfterSubmit.getStatusCode());

        ResponseEntity<Map> cancelled = post(
                "/api/v1/purchase-requests/" + id + "/cancel", admin.token(), Map.of(), Map.class);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode());
        assertNotNull(cancelled.getBody());
        assertEquals("CANCELLED", cancelled.getBody().get("status"));
    }

    @Test
    void itemsAreEditableOnlyInDraft() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID id = createId(admin.token(), "Laptops", List.of(Map.of("description", "Mouse", "quantity", 5, "unitPriceMinor", 2500)));

        ResponseEntity<Map> fetched = get("/api/v1/purchase-requests/" + id, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) fetched.getBody().get("items");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());
        UUID itemId = UUID.fromString(itemList.get(0).get("id").toString());

        ResponseEntity<Map> updated = rest.exchange(
                "/api/v1/purchase-request-items/" + itemId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("quantity", 10), bearer(admin.token())),
                Map.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());

        post("/api/v1/purchase-requests/" + id + "/submit", admin.token(), Map.of(), Map.class);

        ResponseEntity<String> frozen = rest.exchange(
                "/api/v1/purchase-request-items/" + itemId,
                HttpMethod.DELETE,
                new HttpEntity<>(bearer(admin.token())),
                String.class);
        assertEquals(HttpStatus.CONFLICT, frozen.getStatusCode());
    }

    @Test
    void itemsLinkToTenantSuppliersOnly() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID supplier = createSupplier(tenantA.token(), "Acme Parts");
        UUID request = createId(tenantB.token(), "Laptops", null);

        ResponseEntity<String> foreign = post(
                "/api/v1/purchase-requests/" + request + "/items",
                tenantB.token(),
                Map.of("description", "Cable", "quantity", 2, "unitPriceMinor", 1500, "supplierId", supplier),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());

        UUID ownSupplier = createSupplier(tenantB.token(), "Globex Goods");
        ResponseEntity<Map> linked = post(
                "/api/v1/purchase-requests/" + request + "/items",
                tenantB.token(),
                Map.of("description", "Cable", "quantity", 2, "unitPriceMinor", 1500, "supplierId", ownSupplier),
                Map.class);
        assertEquals(HttpStatus.CREATED, linked.getStatusCode());
        assertNotNull(linked.getBody());
        assertEquals(ownSupplier.toString(), linked.getBody().get("supplierId"));
    }

    @Test
    void requestsAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID id = createId(tenantA.token(), "Laptops", null);

        assertEquals(
                HttpStatus.NOT_FOUND, get("/api/v1/purchase-requests/" + id, tenantB.token(), String.class).getStatusCode());

        ResponseEntity<List> list = get("/api/v1/purchase-requests", tenantB.token(), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(list.getBody().isEmpty());
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

    private UUID createId(String token, String title, List<Map<String, Object>> items) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        if (items != null) {
            body.put("items", items);
        }
        ResponseEntity<Map> response = create(token, "key-" + UUID.randomUUID(), body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private ResponseEntity<Map> create(String token, String key, Object body) {
        org.springframework.http.HttpHeaders headers = bearer(token);
        headers.set("Idempotency-Key", key);
        return rest.exchange(
                "/api/v1/purchase-requests", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
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

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private record Fixture(String slug, String token) {
    }
}
