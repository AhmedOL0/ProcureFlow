package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import java.time.Instant;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Approval decisions and delegations: immutable verdicts on submitted
 * requests, the approver/delegate/stranger permission matrix, delegation
 * expiry and revocation, a real two-thread decide race (one verdict wins),
 * and cross-tenant negatives throughout.
 */
class ApprovalIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.procureflow.approval.application.ApprovalService approvals;

    @Test
    void approverApprovesSubmittedRequest() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin);

        ResponseEntity<Map> decision = decide(admin.token(), request, "APPROVED", "Looks good");
        assertEquals(HttpStatus.CREATED, decision.getStatusCode());
        assertNotNull(decision.getBody());
        assertEquals("APPROVED", decision.getBody().get("decision"));
        assertEquals("Looks good", decision.getBody().get("comment"));

        ResponseEntity<Map> fetched = get("/api/v1/purchase-requests/" + request, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        assertEquals("APPROVED", fetched.getBody().get("status"));

        ResponseEntity<Map> recorded = get(
                "/api/v1/approvals/decisions?requestId=" + request, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, recorded.getStatusCode());
        assertNotNull(recorded.getBody());
        assertEquals("APPROVED", recorded.getBody().get("decision"));
    }

    @Test
    void approverRejectsWithComment() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin);

        ResponseEntity<Map> decision = decide(admin.token(), request, "REJECTED", "Over budget");
        assertEquals(HttpStatus.CREATED, decision.getStatusCode());

        ResponseEntity<Map> fetched = get("/api/v1/purchase-requests/" + request, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        assertEquals("REJECTED", fetched.getBody().get("status"));
    }

    @Test
    void decideDraftIsConflict() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID draft = createRequest(admin.token(), "Laptops");

        assertEquals(HttpStatus.CONFLICT, decide(admin.token(), draft, "APPROVED", null).getStatusCode());
    }

    @Test
    void secondDecisionIsConflict() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin);

        assertEquals(HttpStatus.CREATED, decide(admin.token(), request, "APPROVED", null).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, decide(admin.token(), request, "REJECTED", null).getStatusCode());
    }

    @Test
    void memberCannotDecideWithoutDelegation() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        UUID request = submitted(admin);

        assertEquals(HttpStatus.FORBIDDEN, decide(worker.token(), request, "APPROVED", null).getStatusCode());
    }

    @Test
    void delegateDecidesAfterGrant() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        UUID request = submitted(admin);

        ResponseEntity<Map> delegation = delegate(admin.token(), worker.userId(), Instant.now().plusSeconds(3600));
        assertEquals(HttpStatus.CREATED, delegation.getStatusCode());

        ResponseEntity<Map> decision = decide(worker.token(), request, "APPROVED", "On behalf");
        assertEquals(HttpStatus.CREATED, decision.getStatusCode());
        assertNotNull(decision.getBody());
        assertEquals(worker.userId().toString(), decision.getBody().get("deciderId"));
    }

    @Test
    void expiredDelegationCannotDecide() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        UUID request = submitted(admin);

        ResponseEntity<Map> delegation = delegate(admin.token(), worker.userId(), Instant.now().plusSeconds(1));
        assertEquals(HttpStatus.CREATED, delegation.getStatusCode());
        Thread.sleep(1500);

        assertEquals(HttpStatus.FORBIDDEN, decide(worker.token(), request, "APPROVED", null).getStatusCode());
    }

    @Test
    void revokedDelegationCannotDecide() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());
        UUID request = submitted(admin);

        ResponseEntity<Map> delegation = delegate(admin.token(), worker.userId(), Instant.now().plusSeconds(3600));
        assertEquals(HttpStatus.CREATED, delegation.getStatusCode());
        assertNotNull(delegation.getBody());

        ResponseEntity<Void> revoked = rest.exchange(
                "/api/v1/approvals/delegations/" + delegation.getBody().get("id"),
                HttpMethod.DELETE,
                new HttpEntity<>(bearer(admin.token())),
                Void.class);
        assertEquals(HttpStatus.NO_CONTENT, revoked.getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN, decide(worker.token(), request, "APPROVED", null).getStatusCode());
    }

    @Test
    void delegationRulesAreValidated() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture stranger = provision(uniqueSlug("globex"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                delegate(admin.token(), admin.userId(), Instant.now().plusSeconds(60)).getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                delegate(admin.token(), stranger.userId(), Instant.now().minusSeconds(60)).getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                delegate(admin.token(), stranger.userId(), Instant.now().plusSeconds(60)).getStatusCode());
    }

    @Test
    void concurrentDecisionsYieldOneVerdict() throws Exception {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> attempt = () -> {
                gate.await(30, TimeUnit.SECONDS);
                return decide(admin.token(), request, "APPROVED", null);
            };
            Future<ResponseEntity<Map>> first = pool.submit(attempt);
            Future<ResponseEntity<Map>> second = pool.submit(attempt);
            gate.countDown();

            var statusA = first.get(60, TimeUnit.SECONDS).getStatusCode();
            var statusB = second.get(60, TimeUnit.SECONDS).getStatusCode();
            assertTrue(
                    (statusA == HttpStatus.CREATED && statusB == HttpStatus.CONFLICT)
                            || (statusA == HttpStatus.CONFLICT && statusB == HttpStatus.CREATED));

            ResponseEntity<Map> recorded = get(
                    "/api/v1/approvals/decisions?requestId=" + request, admin.token(), Map.class);
            assertEquals(HttpStatus.OK, recorded.getStatusCode());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void decisionsAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID request = submitted(tenantA);

        assertEquals(
                HttpStatus.NOT_FOUND,
                decide(tenantB.token(), request, "APPROVED", null).getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/approvals/decisions?requestId=" + request, tenantB.token(), String.class).getStatusCode());
    }

    @Test
    void assignedApproverDecidesButOutsiderCannot() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture first = join(admin.slug());
        Fixture second = join(admin.slug());
        UUID workflow = createWorkflow(admin.token(), "Standard", 0L, null, 3);
        addStep(admin.token(), workflow, 1, first.userId(), HttpStatus.CREATED);
        addStep(admin.token(), workflow, 2, second.userId(), HttpStatus.CREATED);

        UUID request = submitted(admin);
        assertEquals(
                HttpStatus.FORBIDDEN, decide(second.token(), request, "APPROVED", null).getStatusCode());
        assertEquals(HttpStatus.CREATED, decide(first.token(), request, "APPROVED", null).getStatusCode());

        ResponseEntity<Map> state =
                get("/api/v1/approvals/state?requestId=" + request, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, state.getStatusCode());
        assertNotNull(state.getBody());
        assertEquals(1, ((Number) state.getBody().get("stepOrder")).intValue());
        assertEquals(Boolean.FALSE, state.getBody().get("escalated"));
    }

    @Test
    void escalationHandsAnOverdueRequestToTheNextLevel() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture first = join(admin.slug());
        Fixture second = join(admin.slug());
        UUID workflow = createWorkflow(admin.token(), "Standard", 0L, null, 3);
        addStep(admin.token(), workflow, 1, first.userId(), HttpStatus.CREATED);
        addStep(admin.token(), workflow, 2, second.userId(), HttpStatus.CREATED);

        UUID request = submitted(admin);
        attach(admin.token(), request);
        backdateDueAt(request);

        assertEquals(
                HttpStatus.FORBIDDEN, decide(first.token(), request, "APPROVED", null).getStatusCode());
        assertEquals(HttpStatus.CREATED, decide(second.token(), request, "APPROVED", null).getStatusCode());

        UUID another = submitted(admin);
        attach(admin.token(), another);
        backdateDueAt(another);
        assertEquals(1, approvals.escalateOverdue(Instant.now()));
        ResponseEntity<Map> state = get("/api/v1/approvals/state?requestId=" + another, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, state.getStatusCode());
        assertNotNull(state.getBody());
        assertEquals(2, ((Number) state.getBody().get("stepOrder")).intValue());
        assertEquals(Boolean.TRUE, state.getBody().get("escalated"));
    }

    @Test
    void unmatchedRequestsKeepTheLegacyApproverRule() {
        Fixture admin = provision(uniqueSlug("acme"));
        createWorkflow(admin.token(), "Huge only", 99999999L, null, 3);

        UUID request = submitted(admin);
        assertEquals(HttpStatus.CREATED, decide(admin.token(), request, "APPROVED", null).getStatusCode());

        ResponseEntity<String> state =
                get("/api/v1/approvals/state?requestId=" + request, admin.token(), String.class);
        assertEquals(HttpStatus.NOT_FOUND, state.getStatusCode());
    }

    @Test
    void workflowsAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        UUID workflow = createWorkflow(tenantA.token(), "Standard", 0L, null, 3);

        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/approvals/workflows/" + workflow + "/steps", tenantB.token(), String.class)
                        .getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                addStepRaw(tenantB.token(), workflow, Map.of("stepOrder", 1, "approverId", tenantA.userId()))
                        .getStatusCode());
    }

    @Test
    void workflowAndStepRulesAreValidated() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture stranger = provision(uniqueSlug("globex"));
        UUID workflow = createWorkflow(admin.token(), "Standard", 0L, null, 3);

        assertEquals(
                HttpStatus.NOT_FOUND,
                addStepRaw(admin.token(), workflow, Map.of("stepOrder", 1, "approverId", stranger.userId()))
                        .getStatusCode());
        addStep(admin.token(), workflow, 1, admin.userId(), HttpStatus.CREATED);
        assertEquals(
                HttpStatus.CONFLICT,
                addStepRaw(admin.token(), workflow, Map.of("stepOrder", 1, "approverId", admin.userId()))
                        .getStatusCode());
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
        assertNotNull(response.getBody().user());
        return new Fixture(slug, response.getBody().accessToken(), response.getBody().user().id());
    }

    private UUID submitted(Fixture admin) {
        UUID id = createRequest(admin.token(), "Laptops");
        ResponseEntity<Map> submitted = post(
                "/api/v1/purchase-requests/" + id + "/submit", admin.token(), Map.of(), Map.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        return id;
    }

    private UUID createRequest(String token, String title) {
        ResponseEntity<Map> response = rest.exchange(
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
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
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

    private ResponseEntity<Map> delegate(String token, UUID delegateId, Instant endsAt) {
        return rest.exchange(
                "/api/v1/approvals/delegations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("delegateId", delegateId, "endsAt", endsAt.toString()), bearer(token)),
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

    private UUID createWorkflow(String token, String name, long min, Long max, int days) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("minAmountMinor", min);
        if (max != null) {
            body.put("maxAmountMinor", max);
        }
        body.put("escalateAfterDays", days);
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/approvals/workflows", HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void addStep(String token, UUID workflow, int order, UUID approver, HttpStatus expected) {
        assertEquals(
                expected,
                addStepRaw(token, workflow, Map.of("stepOrder", order, "approverId", approver)).getStatusCode());
    }

    private ResponseEntity<Map> addStepRaw(String token, UUID workflow, Object body) {
        return rest.exchange(
                "/api/v1/approvals/workflows/" + workflow + "/steps",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                Map.class);
    }

    private void attach(String token, UUID requestId) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/approvals/assignments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", requestId), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Boolean.TRUE, response.getBody().get("assigned"));
    }

    private void backdateDueAt(UUID requestId) {
        int updated = jdbc.update(
                "UPDATE approval_assignments SET due_at = ? WHERE request_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),
                requestId);
        assertEquals(1, updated);
    }

    private record Fixture(String slug, String token, UUID userId) {
    }
}
