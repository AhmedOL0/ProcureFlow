package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.application.MailPort;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Bulk supplier onboarding: csv/xlsx import is all-or-nothing with per-row
 * errors, member writes stay 403, and a successful import writes an audit
 * row. Fixtures use random slugs; no inter-test ordering dependence.
 */
class SupplierImportIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @MockitoBean
    private MailPort mail;

    @Test
    void csvImportCreatesSuppliersWithCategories() {
        Fixture admin = provision(uniqueSlug("import"));
        createCategory(admin.token(), "Hardware");

        String csv = """
                name,email,phone,tax_id,address,status,categories
                Acme Parts,acme@test.com,0600000001,DE-1,12 Main St,active,Hardware
                CloudNine,ops@cloudnine.test,,,,,
                """;
        ResponseEntity<Map> imported = importFile(admin.token(), "suppliers.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.OK, imported.getStatusCode());
        assertNotNull(imported.getBody());
        assertEquals(2, imported.getBody().get("created"));

        ResponseEntity<String> search = get("/api/v1/suppliers?q=acme", admin.token(), String.class);
        assertEquals(HttpStatus.OK, search.getStatusCode());
        assertTrue(search.getBody().contains("Acme Parts"));

        UUID id = supplierId(admin.token(), "Acme Parts");
        ResponseEntity<List> assigned = get("/api/v1/suppliers/" + id + "/categories", admin.token(), List.class);
        assertEquals(HttpStatus.OK, assigned.getStatusCode());
        assertNotNull(assigned.getBody());
        assertEquals(1, assigned.getBody().size());
    }

    @Test
    void xlsxImportCreatesSuppliers() throws Exception {
        Fixture admin = provision(uniqueSlug("import"));
        byte[] content;
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Suppliers");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Email");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Bechtle Demo AG");
            row.createCell(1).setCellValue("lina@bechtle.test");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            book.write(out);
            content = out.toByteArray();
        }
        ResponseEntity<Map> imported = importFile(admin.token(), "Suppliers.XLSX", content);
        assertEquals(HttpStatus.OK, imported.getStatusCode());
        assertNotNull(imported.getBody());
        assertEquals(1, imported.getBody().get("created"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidRowsRejectTheWholeFile() {
        Fixture admin = provision(uniqueSlug("import"));
        String csv = """
                name,email,categories
                Good Supplier,good@test.com,
                Bad Supplier,not-an-email,
                Good Supplier,good@test.com,
                ,missing@test.com,
                """;
        ResponseEntity<Map> rejected = importFile(admin.token(), "suppliers.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertNotNull(rejected.getBody());
        assertEquals("IMPORT_VALIDATION_FAILED", rejected.getBody().get("code"));
        Map<String, String> details = (Map<String, String>) rejected.getBody().get("details");
        assertNotNull(details);
        assertTrue(details.containsKey("row.2.email"));
        assertTrue(details.containsKey("row.3.name"));
        assertTrue(details.containsKey("row.4.name"));

        // All-or-nothing: not even the valid first row persisted.
        ResponseEntity<String> search = get("/api/v1/suppliers?q=good", admin.token(), String.class);
        assertEquals(HttpStatus.OK, search.getStatusCode());
        assertFalse(search.getBody().contains("Good Supplier"));
    }

    @Test
    void duplicateOfAnExistingSupplierRejects() {
        Fixture admin = provision(uniqueSlug("import"));
        createSupplier(admin.token(), "Acme Parts");

        String csv = "name\nAcme Parts\n";
        ResponseEntity<Map> rejected = importFile(admin.token(), "s.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals("IMPORT_VALIDATION_FAILED", rejected.getBody().get("code"));
    }

    @Test
    void unknownCategoryRejects() {
        Fixture admin = provision(uniqueSlug("import"));
        String csv = "name,categories\nAcme Parts,Nope\n";
        ResponseEntity<Map> rejected = importFile(admin.token(), "s.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals("IMPORT_VALIDATION_FAILED", rejected.getBody().get("code"));
    }

    @Test
    void missingNameColumnRejects() {
        Fixture admin = provision(uniqueSlug("import"));
        String csv = "email\nacme@test.com\n";
        ResponseEntity<Map> rejected = importFile(admin.token(), "s.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals("MISSING_COLUMNS", rejected.getBody().get("code"));
    }

    @Test
    void foreignFilesAndMasqueradingContentReject() {
        Fixture admin = provision(uniqueSlug("import"));
        byte[] csv = "name\nAcme\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                HttpStatus.BAD_REQUEST,
                importFile(admin.token(), "s.txt", csv).getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                importFile(admin.token(), "s.xlsx", csv).getStatusCode());
    }

    @Test
    void memberCannotImportButAdminImportIsAudited() {
        Fixture admin = provision(uniqueSlug("import"));
        AuthResponse member = joinAsMember(admin, "min@" + admin.slug() + ".test");
        byte[] csv = "name\nAcme\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                HttpStatus.FORBIDDEN,
                importFile(member.accessToken(), "s.csv", csv).getStatusCode());

        assertEquals(HttpStatus.OK, importFile(admin.token(), "s.csv", csv).getStatusCode());
        ResponseEntity<String> trail = get("/api/v1/audit-events?q=SUPPLIER_IMPORTED", admin.token(), String.class);
        assertEquals(HttpStatus.OK, trail.getStatusCode());
        assertTrue(trail.getBody().contains("SUPPLIER_IMPORTED"));
        // The new action is a real trail row, not a frontend invention.
        assertTrue(trail.getBody().contains("Acme") || trail.getBody().contains("supplier"));
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

    private UUID supplierId(String token, String name) {
        // Single search word: RestTemplate double-encodes %20, so multi-word
        // queries go over as literal spaces only via UriComponentsBuilder.
        ResponseEntity<Map> search = get("/api/v1/suppliers?q=Acme", token, Map.class);
        assertEquals(HttpStatus.OK, search.getStatusCode());
        assertNotNull(search.getBody());
        List<Map<String, Object>> content = (List<Map<String, Object>>) search.getBody().get("content");
        return UUID.fromString(content.get(0).get("id").toString());
    }

    private void createSupplier(String token, String name) {
        ResponseEntity<Map> response =
                rest.exchange("/api/v1/suppliers", HttpMethod.POST, new HttpEntity<>(Map.of("name", name), bearer(token)), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void createCategory(String token, String name) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/supplier-categories", HttpMethod.POST, new HttpEntity<>(Map.of("name", name), bearer(token)), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
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
        // Same shape as AuthorizationMatrixIT: admin creates the member, who
        // then logs in directly.
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

    private record Fixture(String slug, String token) {
    }
}
