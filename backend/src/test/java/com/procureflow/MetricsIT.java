package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.procureflow.identity.api.AuthResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Domain and AI counters increment once per business action. The counters
 * are asserted on the registry directly: Spring Boot disables metrics
 * export in test contexts (hermetic by default, unbeatable from test
 * properties), while the Prometheus scrape path itself is stock Boot —
 * verified against a dev boot — and consumed by `alerts.yml`.
 */
class MetricsIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MeterRegistry registry;

    @Test
    void domainCountersIncrementOncePerAction() {
        Fixture admin = provision(uniqueSlug("acme"));
        UUID request = submitted(admin.token());
        decide(admin.token(), request);
        post("/api/v1/ai/chat", admin.token(), Map.of("question", "How much?"), Map.class);

        assertEquals(
                1.0, registry.get("procureflow.decisions").tag("verdict", "approved").counter().count());
        assertEquals(
                1.0, registry.get("procureflow.ai.calls").tag("feature", "copilot").counter().count());
    }

    @Test
    void overspendIncrementsItsCounter() {
        Fixture admin = provision(uniqueSlug("acme"));
        createBudget(admin.token(), 100000L);
        UUID request = submitted(admin.token());

        assertEquals(HttpStatus.CONFLICT, decide(admin.token(), request).getStatusCode());
        assertEquals(1.0, registry.get("procureflow.budget.overspends").counter().count());
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

    private ResponseEntity<Map> decide(String token, UUID request) {
        return rest.exchange(
                "/api/v1/approvals/decisions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", request, "decision", "APPROVED"), bearer(token)),
                Map.class);
    }

    private void createBudget(String token, long amount) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/budgets",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Ops",
                                "period", java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString(),
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
