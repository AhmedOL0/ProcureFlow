package com.procureflow.invoice.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.invoice.application.InvoiceService;
import com.procureflow.invoice.domain.Invoice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
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
    @Operation(summary = "List invoices of the current workspace, newest first")
    public ResponseEntity<List<InvoiceResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Invoice.Status status) {
        return ResponseEntity.ok(invoices.list(principal.tenantId(), status).stream()
                .map(invoice -> InvoiceResponse.from(invoices.match(principal.tenantId(), invoice.getId())))
                .toList());
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
}
