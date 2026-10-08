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
