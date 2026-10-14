package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.application.MailPort;
import com.procureflow.shared.antivirus.ClamAvScanner;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Malware gate on supplier import end to end: with the scanner enforced, a
 * file carrying the harmless EICAR probe answers 422 {@code VIRUS_DETECTED}
 * before any parse and persists nothing, while a clean file still imports.
 * Own containers per class (never an inherited shared one).
 */
class InfectedImportIT extends AbstractIntegrationTest {

    static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Container
    static final GenericContainer<?> CLAMD =
            new GenericContainer<>("clamav/clamav:1.4").withExposedPorts(3310);

    @DynamicPropertySource
    static void antivirus(DynamicPropertyRegistry registry) {
        registry.add("app.antivirus.host", CLAMD::getHost);
        registry.add("app.antivirus.port", () -> CLAMD.getMappedPort(3310));
    }

    @MockitoBean
    private MailPort mail;

    @BeforeAll
    static void awaitSignatures() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(5))
                .ignoreExceptions()
                .until(() -> {
                    new ClamAvScanner(CLAMD.getHost(), CLAMD.getMappedPort(3310), 10000)
                            .scan("name\nAcme\n".getBytes(StandardCharsets.UTF_8));
                    return true;
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void infectedFileIsRejectedBeforeAnyParse() {
        Fixture admin = provision(uniqueSlug("infected"));
        ResponseEntity<Map> rejected =
                importFile(admin.token(), "suppliers.csv", EICAR.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, rejected.getStatusCode());
        assertNotNull(rejected.getBody());
        assertEquals("VIRUS_DETECTED", rejected.getBody().get("code"));

        ResponseEntity<Map> directory = get("/api/v1/suppliers?page=0&size=20", admin.token(), Map.class);
        assertEquals(HttpStatus.OK, directory.getStatusCode());
        assertNotNull(directory.getBody());
        assertEquals(0, ((Number) directory.getBody().get("totalElements")).intValue());
    }

    @Test
    void cleanFileStillImportsWhenScanningIsEnforced() {
        Fixture admin = provision(uniqueSlug("clean"));
        byte[] csv = "name\nAcme\n".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<Map> imported = importFile(admin.token(), "s.csv", csv);
        assertEquals(HttpStatus.OK, imported.getStatusCode());
        assertNotNull(imported.getBody());
        assertEquals(1, imported.getBody().get("created"));
    }

    private ResponseEntity<Map> importFile(String token, String filename, byte[] content) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return rest.exchange(
                "/api/v1/suppliers/import", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
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
