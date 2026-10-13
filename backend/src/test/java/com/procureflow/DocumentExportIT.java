package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.application.MailPort;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Generated documents: order/invoice PDFs carry the real parties, lines and
 * totals; xlsx exports round-trip through POI; cross-tenant document reads
 * stay 404. Members keep every export (they hold the read authorities).
 */
class DocumentExportIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @MockitoBean
    private MailPort mail;

    @Test
    void orderDocumentIsAPdfWithPartiesAndTotals() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID order = createOrder(admin.token(), approved(admin.token()), supplier);

        ResponseEntity<byte[]> document = getBytes("/api/v1/purchase-orders/" + order + "/document.pdf", admin.token());
        assertEquals(HttpStatus.OK, document.getStatusCode());
        assertTrue(document.getHeaders().getContentType().toString().contains("application/pdf"));
        assertTrue(document.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("purchase-order-"));
        String text = pdfText(document.getBody());
        assertTrue(text.contains("Purchase order"));
        assertTrue(text.contains("Acme Parts"));
        assertTrue(text.contains("ThinkPad"));
        assertTrue(text.contains("MAD 1,798.00"));
        assertTrue(text.contains("Page 1 of 1"));
    }

    @Test
    void documentsAreInvisibleAcrossTenants() {
        Fixture tenantA = provision(uniqueSlug("docs"));
        Fixture tenantB = provision(uniqueSlug("docs"));
        UUID order = createOrder(tenantA.token(), approved(tenantA.token()), createSupplier(tenantA.token(), "Acme"));

        assertEquals(
                HttpStatus.NOT_FOUND,
                getBytes("/api/v1/purchase-orders/" + order + "/document.pdf", tenantB.token()).getStatusCode());
    }

    @Test
    void ordersExportRoundTripsThroughPoi() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        createOrder(admin.token(), approved(admin.token()), createSupplier(admin.token(), "Acme Parts"));

        ResponseEntity<byte[]> exported = getBytes("/api/v1/purchase-orders/export.xlsx", admin.token());
        assertEquals(HttpStatus.OK, exported.getStatusCode());
        assertTrue(exported.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("purchase-orders-"));
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            var row = book.getSheet("Orders").getRow(1);
            assertEquals("Acme Parts", row.getCell(1).getStringCellValue());
            assertTrue(row.getCell(5).getNumericCellValue() > 0);
        }
    }

    @Test
    void invoiceDocumentAndExportCarryPaidAndBalance() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID order = sentOrder(admin.token(), approved(admin.token()), supplier);
        UUID line = orderLine(admin.token(), order);
        UUID invoice = createInvoice(
                admin.token(), order, "INV-1", List.of(Map.of("orderItemId", line, "quantity", 1)));
        pay(admin.token(), invoice, 50000);

        String text = pdfText(getBytes("/api/v1/invoices/" + invoice + "/document.pdf", admin.token()).getBody());
        assertTrue(text.contains("Invoice INV-1"));
        assertTrue(text.contains("Acme Parts"));
        assertTrue(text.contains("ThinkPad"));
        assertTrue(text.contains("MAD 899.00"));
        assertTrue(text.contains("MAD 500.00"));
        assertTrue(text.contains("MAD 399.00"));

        ResponseEntity<byte[]> exported = getBytes("/api/v1/invoices/export.xlsx", admin.token());
        assertEquals(HttpStatus.OK, exported.getStatusCode());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            var row = book.getSheet("Invoices").getRow(1);
            assertEquals("INV-1", row.getCell(0).getStringCellValue());
            assertEquals(899.0, row.getCell(5).getNumericCellValue());
            assertEquals(500.0, row.getCell(6).getNumericCellValue());
            assertEquals(399.0, row.getCell(7).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void requestDocumentIsAPdfWithLines() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        UUID request = approved(admin.token());

        ResponseEntity<byte[]> document =
                getBytes("/api/v1/purchase-requests/" + request + "/document.pdf", admin.token());
        assertEquals(HttpStatus.OK, document.getStatusCode());
        String text = pdfText(document.getBody());
        assertTrue(text.contains("Purchase request"));
        assertTrue(text.contains("Laptops"));
        assertTrue(text.contains("ThinkPad"));
        assertTrue(text.contains("MAD 1,798.00"));
        assertTrue(text.contains("Page 1 of 1"));
    }

    @Test
    void auditExportRoundTrips() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        createBudget(admin.token());

        ResponseEntity<byte[]> exported =
                getBytes("/api/v1/audit-events/export.xlsx?q=BUDGET_CREATED", admin.token());
        assertEquals(HttpStatus.OK, exported.getStatusCode());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            var row = book.getSheet("Audit").getRow(1);
            assertEquals("BUDGET_CREATED", row.getCell(2).getStringCellValue());
            assertEquals("budget", row.getCell(3).getStringCellValue());
        }
    }

    @Test
    void requestsExportCarriesRequesterAndTotal() throws Exception {        Fixture admin = provision(uniqueSlug("docs"));
        createRequest(admin.token());

        ResponseEntity<byte[]> exported = getBytes("/api/v1/purchase-requests/export.xlsx", admin.token());
        assertEquals(HttpStatus.OK, exported.getStatusCode());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            var row = book.getSheet("Requests").getRow(1);
            assertEquals("Laptops", row.getCell(0).getStringCellValue());
            assertTrue(row.getCell(1).getStringCellValue().contains("@"));
            assertEquals(1798.0, row.getCell(5).getNumericCellValue());
        }
    }

    @Test
    void suppliersExportGuardsFormulaCells() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        createSupplier(admin.token(), "=cmd|'/c calc'!A0");

        ResponseEntity<byte[]> exported = getBytes("/api/v1/suppliers/export.xlsx", admin.token());
        assertEquals(HttpStatus.OK, exported.getStatusCode());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            String stored = book.getSheet("Suppliers").getRow(1).getCell(0).getStringCellValue();
            assertTrue(stored.startsWith("'"));
        }
    }

    @Test
    void spendReportCoversBothFormats() throws Exception {
        Fixture admin = provision(uniqueSlug("docs"));
        createOrder(admin.token(), approved(admin.token()), createSupplier(admin.token(), "Acme Parts"));

        ResponseEntity<byte[]> pdf = getBytes("/api/v1/analytics/spend/report.pdf", admin.token());
        assertEquals(HttpStatus.OK, pdf.getStatusCode());
        String text = pdfText(pdf.getBody());
        assertTrue(text.contains("Spend report"));
        assertTrue(text.contains("MAD 1,798.00"));

        ResponseEntity<byte[]> xlsx = getBytes("/api/v1/analytics/spend/report.xlsx", admin.token());
        assertEquals(HttpStatus.OK, xlsx.getStatusCode());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getBody()))) {
            assertNotNull(book.getSheet("Totals"));
            assertNotNull(book.getSheet("Categories"));
            assertNotNull(book.getSheet("Periods"));
            assertNotNull(book.getSheet("Suppliers"));
        }
    }

    @Test
    void membersKeepEveryExport() {
        Fixture admin = provision(uniqueSlug("docs"));
        AuthResponse member = member(admin);
        for (String url : List.of(
                "/api/v1/suppliers/export.xlsx",
                "/api/v1/purchase-orders/export.xlsx",
                "/api/v1/invoices/export.xlsx",
                "/api/v1/purchase-requests/export.xlsx",
                "/api/v1/analytics/spend/report.pdf",
                "/api/v1/analytics/spend/report.xlsx")) {
            assertEquals(HttpStatus.OK, getBytes(url, member.accessToken()).getStatusCode(), url);
        }
        UUID supplier = createSupplier(admin.token(), "Acme Parts");
        UUID order = createOrder(admin.token(), approved(admin.token()), supplier);
        assertEquals(
                HttpStatus.OK,
                getBytes("/api/v1/purchase-orders/" + order + "/document.pdf", member.accessToken()).getStatusCode());
    }

    private String pdfText(byte[] content) throws Exception {
        assertNotNull(content);
        assertEquals("%PDF-", new String(content, 0, 5, StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private ResponseEntity<byte[]> getBytes(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer(token)), byte[].class);
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

    private AuthResponse member(Fixture admin) {
        String email = "min@" + admin.slug() + ".test";
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

    private UUID createSupplier(String token, String name) {        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/suppliers", HttpMethod.POST, new HttpEntity<>(Map.of("name", name), bearer(token)), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void createBudget(String token) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "Ops");
        body.put("period", java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString());
        body.put("amountMinor", 500000L);
        ResponseEntity<Map> response =
                rest.exchange("/api/v1/budgets", HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private UUID createRequest(String token) {
        ResponseEntity<Map> response = rest.exchange(
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
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID approved(String token) {
        UUID id = createRequest(token);
        assertEquals(
                HttpStatus.OK,
                post("/api/v1/purchase-requests/" + id + "/submit", token).getStatusCode());
        ResponseEntity<Map> decided = rest.exchange(
                "/api/v1/approvals/decisions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", id, "decision", "APPROVED"), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, decided.getStatusCode());
        return id;
    }

    private UUID createOrder(String token, UUID request, UUID supplier) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/purchase-orders",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", request, "supplierId", supplier), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID sentOrder(String token, UUID request, UUID supplier) {
        UUID order = createOrder(token, request, supplier);
        assertEquals(HttpStatus.OK, post("/api/v1/purchase-orders/" + order + "/send", token).getStatusCode());
        return order;
    }

    private UUID orderLine(String token, UUID order) {
        ResponseEntity<Map> fetched = get("/api/v1/purchase-orders/" + order, token, Map.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertNotNull(fetched.getBody());
        List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.getBody().get("lines");
        return UUID.fromString(lines.get(0).get("id").toString());
    }

    private UUID createInvoice(String token, UUID order, String number, Object lines) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/invoices",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", order, "number", number, "lines", lines), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void pay(String token, UUID invoice, long amountMinor) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/invoices/" + invoice + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amountMinor", amountMinor), bearer(token)),
                Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private HttpHeaders withIdempotency(String token) {
        HttpHeaders headers = bearer(token);
        headers.set("Idempotency-Key", "key-" + UUID.randomUUID());
        return headers;
    }

    private ResponseEntity<String> post(String url, String token) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of(), bearer(token)), String.class);
    }

    private record Fixture(String slug, String token) {
    }
}
