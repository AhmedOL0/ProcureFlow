package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.application.MailPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * History is derived: with Mongo unreachable the extract answer still
 * returns 200 (gap metered, never silent) while history reads fail loud
 * with 503 instead of pretending the shelf is empty.
 */
class ExtractionDegradedIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @DynamicPropertySource
    static void deadMongo(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.data.mongodb.uri",
                () -> "mongodb://localhost:9/history?serverSelectionTimeoutMS=1000&connectTimeoutMS=1000");
    }

    @MockitoBean
    private MailPort mail;

    @Autowired
    private MeterRegistry meters;

    @Test
    @SuppressWarnings("unchecked")
    void extractStillAnswersWhileHistoryDropsMetered() {
        Fixture admin = provision(uniqueSlug("acme"));

        ResponseEntity<Map> draft = rest.exchange(
                "/api/v1/ai/extract-request",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("question", "20 laptops please"), bearer(admin.token())),
                Map.class);
        assertEquals(HttpStatus.OK, draft.getStatusCode());
        assertEquals("Laptops", draft.getBody().get("title"));
        assertEquals(
                1.0,
                meters.counter("procureflow.ai.history_dropped").count());

        ResponseEntity<Map> history =
                get("/api/v1/ai/extractions?page=0&size=20", admin.token(), Map.class);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, history.getStatusCode());
        assertNotNull(history.getBody());
        assertEquals("HISTORY_UNAVAILABLE", history.getBody().get("code"));
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

    private record Fixture(String slug, String token) {
    }
}
