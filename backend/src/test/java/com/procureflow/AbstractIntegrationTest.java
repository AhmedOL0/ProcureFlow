package com.procureflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared integration-test bootstrap: random web port, test profile, HTTP
 * helpers. NOTE: the PostgreSQL container is intentionally NOT declared here.
 * A static @Container inherited from a parent is started once and stopped
 * after the first subclass finishes, leaving later subclasses pointed at a
 * dead port — each concrete test class declares its own static container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * Digest-pinned Postgres for every suite. A digest (unlike a floating
     * tag) makes Testcontainers skip the remote freshness check and use the
     * runner cache, so CI no longer burns 13 Hub lookups per run — the
     * failure mode that killed a full green suite on a Hub hiccup. Bump the
     * digest deliberately when a new alpine rebuild is wanted.
     */
    protected static final String POSTGRES_IMAGE =
            "postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea";

    /**
     * One container per test class (see the class javadoc), all from the
     * pinned digest above. The explicit substitute declaration is required
     * because Testcontainers rejects digest-pinned names otherwise.
     */
    protected static org.testcontainers.containers.PostgreSQLContainer<?> postgresContainer() {
        return new org.testcontainers.containers.PostgreSQLContainer<>(
                org.testcontainers.utility.DockerImageName.parse(POSTGRES_IMAGE)
                        .asCompatibleSubstituteFor("postgres"));
    }

    @Autowired
    protected TestRestTemplate rest;

    protected static String uniqueSlug(String prefix) {
        return (prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8)).toLowerCase();
    }

    protected HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    protected <T> ResponseEntity<T> get(String url, String accessToken, Class<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), type);
    }
}
