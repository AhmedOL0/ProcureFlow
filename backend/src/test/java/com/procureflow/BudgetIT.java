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
 * Budget pots and in-transaction reservation: approving holds the total,
 * overspend answers 409 without changing the request, rejection holds
 * nothing, cancelling an approved request releases, concurrent approvers
 * serialize on the pot row, and tenants never see each other's pots.
 */
class BudgetIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Test
    void approveReservesBudget() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID budget = createBudget(admin.token(), "Ops", currentPeriod(), 500000L, "MAD");
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CREATED, decide(admin.token(), request, "APPROVED").getStatusCode());

        ResponseEntity<Map> fetched = get("/api/v1/budgets/" + budget, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        assertEquals(179800L, ((Number) fetched.getBody().get("reservedMinor")).longValue());
        assertEquals(320200L, ((Number) fetched.getBody().get("remainingMinor")).longValue());

        ResponseEntity<List> holds = get("/api/v1/budgets/" + budget + "/reservations", admin.token(), List.class);
        assertEquals(HttpStatus.OK, holds.getStatusCode());
        assertNotNull(holds.getBody());
        assertEquals(1, holds.getBody().size());
    }

    @Test
    void overspendRejectedLeavesRequestSubmitted() {
        Fixture admin = provision(uniqueSlug("acme"));
        createBudget(admin.token(), "Ops", currentPeriod(), 100000L, "MAD");
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CONFLICT, decide(admin.token(), request, "APPROVED").getStatusCode());

        ResponseEntity<Map> fetched = get("/api/v1/purchase-requests/" + request, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        assertEquals("SUBMITTED", fetched.getBody().get("status"));

        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/approvals/decisions?requestId=" + request, admin.token(), String.class).getStatusCode());
    }

    @Test
    void approveWithoutBudgetProceedsUnreserved() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CREATED, decide(admin.token(), request, "APPROVED").getStatusCode());
    }

    @Test
    void rejectCreatesNoReservation() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID budget = createBudget(admin.token(), "Ops", currentPeriod(), 500000L, "MAD");
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CREATED, decide(admin.token(), request, "REJECTED").getStatusCode());
        assertEquals(0L, reserved(admin.token(), budget));
    }

    @Test
    void cancelReleasesReservation() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID budget = createBudget(admin.token(), "Ops", currentPeriod(), 500000L, "MAD");
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CREATED, decide(admin.token(), request, "APPROVED").getStatusCode());
        assertEquals(179800L, reserved(admin.token(), budget));

        ResponseEntity<Map> cancelled = post(
                "/api/v1/purchase-requests/" + request + "/cancel", admin.token(), Map.of(), Map.class);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode());
        assertEquals(0L, reserved(admin.token(), budget));
    }

    @Test
    void concurrentApprovalsOnLimitedBudgetYieldOneHold() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID budget = createBudget(admin.token(), "Ops", currentPeriod(), 200000L, "MAD");
        UUID first = submitted(admin.token());
        UUID second = submitted(admin.token());
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> a = pool.submit(decideAfterGate(gate, admin.token(), first));
            Future<ResponseEntity<Map>> b = pool.submit(decideAfterGate(gate, admin.token(), second));
            gate.countDown();

            var statusA = a.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = b.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(
                    (statusA == HttpStatus.CREATED && statusB == HttpStatus.CONFLICT)
                            || (statusA == HttpStatus.CONFLICT && statusB == HttpStatus.CREATED));
            assertEquals(179800L, reserved(admin.token(), budget));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void budgetsAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID budget = createBudget(tenantA.token(), "Ops", currentPeriod(), 500000L, "MAD");

        assertEquals(
                HttpStatus.NOT_FOUND, get("/api/v1/budgets/" + budget, tenantB.token(), String.class).getStatusCode());

        ResponseEntity<Map> list = get("/api/v1/budgets", tenantB.token(), Map.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(((List<?>) list.getBody().get("content")).isEmpty());

        UUID foreign = submitted(tenantB.token());
        assertEquals(HttpStatus.CREATED, decide(tenantB.token(), foreign, "APPROVED").getStatusCode());
        assertEquals(0L, reserved(tenantA.token(), budget));
    }

    @Test
    void deleteBlockedWhileReserved() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID budget = createBudget(admin.token(), "Ops", currentPeriod(), 500000L, "MAD");
        UUID request = submitted(admin.token());
        decide(admin.token(), request, "APPROVED");

        assertEquals(
                HttpStatus.CONFLICT,
                rest.exchange(
                                "/api/v1/budgets/" + budget,
                                HttpMethod.DELETE,
                                new HttpEntity<>(bearer(admin.token())),
                                String.class)
                        .getStatusCode());

        post("/api/v1/purchase-requests/" + request + "/cancel", admin.token(), Map.of(), Map.class);
        assertEquals(
                HttpStatus.NO_CONTENT,
                rest.exchange(
                                "/api/v1/budgets/" + budget,
                                HttpMethod.DELETE,
                                new HttpEntity<>(bearer(admin.token())),
                                Void.class)
                        .getStatusCode());
    }

    @Test
    void budgetInputValidated() {
        Fixture admin = provision(uniqueSlug("acme"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                createBudgetRaw(admin.token(), Map.of("name", "Ops", "period", "2026-13", "amountMinor", 1000))
                        .getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                createBudgetRaw(
                                admin.token(),
                                Map.of("name", "Ops", "period", currentPeriod(), "amountMinor", -5))
                        .getStatusCode());
    }

    @Test
    void currencyMismatchRejected() {
        Fixture admin = provision(uniqueSlug("acme"));
        createBudget(admin.token(), "Ops", currentPeriod(), 500000L, "EUR");
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CONFLICT, decide(admin.token(), request, "APPROVED").getStatusCode());
    }

    private Callable<ResponseEntity<Map>> decideAfterGate(CountDownLatch gate, String token, UUID request) {
        return () -> {
            gate.await(30, TimeUnit.SECONDS);
            return decide(token, request, "APPROVED");
        };
    }

    private long reserved(String token, UUID budget) {
        ResponseEntity<Map> fetched = get("/api/v1/budgets/" + budget, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        return ((Number) fetched.getBody().get("reservedMinor")).longValue();
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

    private String currentPeriod() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private UUID createBudget(String token, String name, String period, long amount, String currency) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("period", period);
        body.put("amountMinor", amount);
        body.put("currency", currency);
        ResponseEntity<Map> response = createBudgetRaw(token, body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private ResponseEntity<Map> createBudgetRaw(String token, Object body) {
        return rest.exchange("/api/v1/budgets", HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
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
        ResponseEntity<Map> submitted =
                post("/api/v1/purchase-requests/" + id + "/submit", token, Map.of(), Map.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        return id;
    }

    private ResponseEntity<Map> decide(String token, UUID requestId, String decision) {
        return rest.exchange(
                "/api/v1/approvals/decisions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", requestId, "decision", decision), bearer(token)),
                Map.class);
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
