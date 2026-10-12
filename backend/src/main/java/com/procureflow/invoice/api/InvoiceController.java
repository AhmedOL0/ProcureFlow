package com.procureflow.invoice.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.invoice.application.InvoiceDocumentService;
import com.procureflow.invoice.application.InvoiceService;
import com.procureflow.invoice.domain.Invoice;
import com.procureflow.shared.document.Filenames;
import com.procureflow.shared.document.Spreadsheet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.procureflow.shared.web.Paged;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supplier invoices with a 3-way-match-lite view (ordered vs received vs
 * invoiced per order line). Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "invoices", description = "Invoices on sent orders with payment status")
public class InvoiceController {

    private final InvoiceService invoices;
    private final InvoiceDocumentService documents;

    public InvoiceController(InvoiceService invoices, InvoiceDocumentService documents) {
        this.invoices = invoices;
        this.documents = documents;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('invoice:write')")
    @Operation(summary = "Book an invoice against a sent order (over-invoicing rejected)")
    public ResponseEntity<InvoiceResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateInvoiceRequest request) {
        Invoice invoice = invoices.create(
                principal.tenantId(),
                principal.userId(),
                request.orderId(),
                request.number(),
                request.lines().stream()
                        .map(line -> new InvoiceService.LineInput(line.orderItemId(), line.quantity()))
                        .toList());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvoiceResponse.from(invoices.match(principal.tenantId(), invoice.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('invoice:read')")
    @Operation(summary = "Paged invoices of the current workspace, newest first")
    public ResponseEntity<Paged<InvoiceResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Invoice.Status status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // Match enrichment runs per page row, bounded by the page size.
        return ResponseEntity.ok(invoices
                .page(principal.tenantId(), status, query, Paged.pageOrThrow(page), Paged.sizeOrThrow(size))
                .map(invoice -> InvoiceResponse.from(invoices.match(principal.tenantId(), invoice.getId()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:read')")
    @Operation(summary = "One invoice with its 3-way match and paid total")
    public ResponseEntity<InvoiceResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(InvoiceResponse.from(invoices.match(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('invoice:write')")
    @Operation(summary = "Record a payment (UNPAID → PARTIAL → PAID)")
    public ResponseEntity<InvoiceResponse> pay(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody PayInvoiceRequest request) {
        Invoice paid = invoices.pay(principal.tenantId(), principal.userId(), id, request.amountMinor());
        return ResponseEntity.ok(InvoiceResponse.from(invoices.match(principal.tenantId(), paid.getId())));
    }

    @GetMapping("/{id}/document.pdf")
    @PreAuthorize("hasAuthority('invoice:read')")
    @Operation(summary = "Printable invoice (supplier, lines, total, paid, balance)")
    public ResponseEntity<byte[]> document(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        byte[] content = documents.document(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.pdf("invoice", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize("hasAuthority('invoice:read')")
    @Operation(summary = "Export filtered invoices as xlsx (max 5000 rows)")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Invoice.Status status,
            @RequestParam(required = false, name = "q") String query) {
        List<List<Object>> body = documents
                .exportRows(principal.tenantId(), status, query, principal.userId())
                .stream()
                .map(row -> List.<Object>of(
                        row.number(),
                        row.order(),
                        row.supplier(),
                        row.status(),
                        row.currency(),
                        row.totalMinor() / 100.0,
                        row.paidMinor() / 100.0,
                        (row.totalMinor() - row.paidMinor()) / 100.0,
                        row.createdAt()))
                .toList();
        byte[] content = Spreadsheet.book(
                "Invoices",
                List.of(
                        "number", "order", "supplier", "status", "currency", "total", "paid", "balance",
                        "created_at"),
                body);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.xlsx("invoices", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
