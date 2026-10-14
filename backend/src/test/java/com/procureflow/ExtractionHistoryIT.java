package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.application.MailPort;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * AI extraction history in MongoDB: validated drafts persist with
 * provenance, read back paged and tenant-scoped, unknown and foreign ids
 * answer 404. Own containers per class (never an inherited shared one).
 */
class ExtractionHistoryIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.data.mongodb.uri",
                () -> MONGO.getConnectionString() + "/extractions-test?serverSelectionTimeoutMS=2000");
    }

    @MockitoBean
    private MailPort mail;

    @Autowired
    private MongoTemplate mongo;

    @Test
    @SuppressWarnings("unchecked")
    void extractPersistsValidatedDraftAndListsIt() {
        Fixture admin = provision(uniqueSlug("acme"));

        ResponseEntity<Map> draft = post(
                "/api/v1/ai/extract-request", admin.token(), Map.of("question", "20 laptops please"), Map.class);
        assertEquals(HttpStatus.OK, draft.getStatusCode());
        assertEquals("Laptops", draft.getBody().get("title"));

        ResponseEntity<Map> history = get("/api/v1/ai/extractions?page=0&size=20", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, history.getStatusCode());
        assertNotNull(history.getBody());
        assertEquals(1L, ((Number) history.getBody().get("totalElements")).longValue());
        List<Map<String, Object>> content = (List<Map<String, Object>>) history.getBody().get("content");
        Map<String, Object> row = content.get(0);
        assertEquals("Laptops", row.get("title"));
        assertEquals("fake-test", row.get("modelIdentifier"));
        assertNotNull(row.get("promptVersion"));
        assertEquals("20 laptops please", row.get("input"));
        assertEquals(1, ((Number) row.get("schemaVersion")).intValue());
        assertEquals(1, ((List<?>) row.get("items")).size());

        ResponseEntity<Map> one = get("/api/v1/ai/extractions/" + row.get("id"), admin.token(), Map.class);
        assertEquals(HttpStatus.OK, one.getStatusCode());
        assertEquals("Laptops", one.getBody().get("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void crossTenantReadsAnswerNotFound() {
        Fixture admin = provision(uniqueSlug("acme"));
        post("/api/v1/ai/extract-request", admin.token(), Map.of("question", "20 laptops please"), Map.class);
        ResponseEntity<Map> history = get("/api/v1/ai/extractions?page=0&size=20", admin.token(), Map.class);
        String id = ((List<Map<String, Object>>) history.getBody().get("content")).get(0).get("id").toString();

        Fixture other = provision(uniqueSlug("globex"));
        ResponseEntity<Map> foreignList =
                get("/api/v1/ai/extractions?page=0&size=20", other.token(), Map.class);
        assertEquals(HttpStatus.OK, foreignList.getStatusCode());
        assertEquals(0L, ((Number) foreignList.getBody().get("totalElements")).longValue());

        // Unknown ids and foreign rows are indistinguishable: 404 either way.
        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/ai/extractions/" + id, other.token(), Map.class).getStatusCode());
        assertEquals(
                HttpStatus.NOT_FOUND,
                get("/api/v1/ai/extractions/000000000000000000000000", admin.token(), Map.class)
                        .getStatusCode());
    }

    @Test
    void memberWithoutAiUseIsDenied() {
        Fixture admin = provision(uniqueSlug("acme"));
        AuthResponse member = joinAsMember(admin, "member@member.test");
        assertEquals(
                HttpStatus.FORBIDDEN,
                get("/api/v1/ai/extractions?page=0&size=20", member.accessToken(), Map.class).getStatusCode());
    }

    @Test
    void tenantAndTtlIndexesExist() {
        boolean compound = false;
        boolean ttl = false;
        for (Document index : mongo.getDb().getCollection("ai_extractions").listIndexes()) {
            Document key = (Document) index.get("key");
            if (key != null && Integer.valueOf(1).equals(key.get("tenantSlug")) && key.containsKey("createdAt")) {
                compound = true;
            }
            if (Long.valueOf(7776000L).equals(toLong(index.get("expireAfterSeconds")))) {
                ttl = true;
            }
        }
        assertTrue(compound, "compound (tenantSlug, createdAt) index missing");
        assertTrue(ttl, "90-day TTL index missing (expected expireAfterSeconds=7776000)");
    }

    private static Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
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

    private AuthResponse joinAsMember(Fixture admin, String email) {
        ResponseEntity<AuthResponse> created = rest.exchange(
                "/api/v1/users",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("email", email, "password", "correct-horse-123", "roleNames", List.of("MEMBER")),
                        bearer(admin.token())),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        ResponseEntity<AuthResponse> login = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "correct-horse-123", "tenantSlug", admin.slug()),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody());
        return login.getBody();
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), type);
    }

    private record Fixture(String slug, String token) {
    }
}
