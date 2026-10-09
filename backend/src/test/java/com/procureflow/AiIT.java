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
 * Procurement copilot behind the test-double provider: KPI-cited answers,
 * validated extraction drafts, quotation comparison, per-tenant metering.
 * Members without {@code ai:use} answer 403; tenants never share usage.
 */
class AiIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void chatAnswersWithCitationsAndMeters() {
        Fixture admin = provision(uniqueSlug("acme"));

        ResponseEntity<Map> answer = post(
                "/api/v1/ai/chat", admin.token(), Map.of("question", "How much did we order?"), Map.class);
        assertEquals(HttpStatus.OK, answer.getStatusCode());
        assertNotNull(answer.getBody());
        assertTrue(answer.getBody().get("answer").toString().contains("Workspace answer"));
        assertNotNull(answer.getBody().get("citations"));
        assertEquals(4, ((List<?>) answer.getBody().get("citations")).size());
        assertEquals("fake-test", answer.getBody().get("model"));

        ResponseEntity<Map> usage = get("/api/v1/ai/usage", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, usage.getStatusCode());
        assertNotNull(usage.getBody());
        assertEquals(1L, ((Number) usage.getBody().get("calls")).longValue());
    }

    @Test
    void extractParsesDraft() {
        Fixture admin = provision(uniqueSlug("acme"));

        ResponseEntity<Map> draft = post(
                "/api/v1/ai/extract-request", admin.token(), Map.of("question", "20 laptops please"), Map.class);
        assertEquals(HttpStatus.OK, draft.getStatusCode());
        assertNotNull(draft.getBody());
        assertEquals("Laptops", draft.getBody().get("title"));
        assertEquals("HIGH", draft.getBody().get("priority"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) draft.getBody().get("items");
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("ThinkPad", items.get(0).get("description"));
        assertEquals(20, ((Number) items.get(0).get("quantity")).intValue());
    }

    @Test
    void compareNeedsTwoQuotes() {
        Fixture admin = provision(uniqueSlug("acme"));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                post("/api/v1/ai/compare-quotations", admin.token(),
                        Map.of("quotes", List.of(Map.of("supplier", "Solo", "amountMinor", 100))), Map.class)
                        .getStatusCode());

        ResponseEntity<Map> compared = post(
                "/api/v1/ai/compare-quotations",
                admin.token(),
                Map.of("quotes", List.of(
                        Map.of("supplier", "Acme", "amountMinor", 179800),
                        Map.of("supplier", "Globex", "amountMinor", 165000))),
                Map.class);
        assertEquals(HttpStatus.OK, compared.getStatusCode());
        assertNotNull(compared.getBody());
        assertTrue(compared.getBody().get("answer").toString().contains("Cheapest"));
    }

    @Test
    void explainSpendAndUsagePeriods() {
        Fixture admin = provision(uniqueSlug("acme"));

        assertEquals(
                HttpStatus.OK,
                post("/api/v1/ai/explain-spend", admin.token(), Map.of(), Map.class).getStatusCode());

        String month = YearMonth.now(ZoneOffset.UTC).toString();
        ResponseEntity<Map> usage = get("/api/v1/ai/usage?period=" + month, admin.token(), Map.class);
        assertEquals(HttpStatus.OK, usage.getStatusCode());
        assertNotNull(usage.getBody());
        assertEquals(1L, ((Number) usage.getBody().get("calls")).longValue());

        ResponseEntity<Map> empty = get("/api/v1/ai/usage?period=2000-01", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, empty.getStatusCode());
        assertNotNull(empty.getBody());
        assertEquals(0L, ((Number) empty.getBody().get("calls")).longValue());
    }

    @Test
    void membersWithoutAiUseAreForbidden() {
        Fixture admin = provision(uniqueSlug("acme"));
        Fixture worker = join(admin.slug());

        assertEquals(
                HttpStatus.FORBIDDEN,
                post("/api/v1/ai/chat", worker.token(), Map.of("question", "Hi?"), String.class).getStatusCode());
    }

    @Test
    void usageIsTenantScoped() {
        Fixture tenantA = provision(uniqueSlug("acme"));
        Fixture tenantB = provision(uniqueSlug("globex"));
        post("/api/v1/ai/chat", tenantA.token(), Map.of("question", "How much?"), Map.class);

        ResponseEntity<Map> foreign = get("/api/v1/ai/usage", tenantB.token(), Map.class);
        assertEquals(HttpStatus.OK, foreign.getStatusCode());
        assertNotNull(foreign.getBody());
        assertEquals(0L, ((Number) foreign.getBody().get("calls")).longValue());
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

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private record Fixture(String slug, String token) {
    }
}
