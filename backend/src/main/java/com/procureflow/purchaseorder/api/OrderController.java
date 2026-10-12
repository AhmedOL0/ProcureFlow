package com.procureflow.purchaseorder.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.purchaseorder.application.OrderDocumentService;
import com.procureflow.purchaseorder.application.OrderService;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
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
 * Supplier orders. Creation needs an APPROVED request (lines snapshotted,
 * supplier mandatory); sending flips the request to ORDERED in the same
 * transaction. Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "purchase-orders", description = "Orders from approved requests through receipt")
public class OrderController {

    private final OrderService orders;
    private final OrderDocumentService documents;

    public OrderController(OrderService orders, OrderDocumentService documents) {
        this.orders = orders;
        this.documents = documents;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Start a draft order from one approved request")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateOrderRequest request) {
        PurchaseOrder order = orders.create(
                principal.tenantId(), principal.userId(), request.requestId(), request.supplierId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order, orders.lines(principal.tenantId(), order.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    @Operation(summary = "Paged orders of the current workspace, newest first")
    public ResponseEntity<Paged<OrderResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) PurchaseOrder.Status status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // Null status lists everything; the repository branches so Hibernate
        // never sees a null parameter (see gotcha 9). Line enrichment runs
        // per page row, bounded by the page size.
        return ResponseEntity.ok(orders
                .page(principal.tenantId(), status, Paged.pageOrThrow(page), Paged.sizeOrThrow(size))
                .map(order -> OrderResponse.from(order, orders.lines(principal.tenantId(), order.getId()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:read')")
    @Operation(summary = "One order with its lines and total")
    public ResponseEntity<OrderResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(
                OrderResponse.from(orders.get(principal.tenantId(), id), orders.lines(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Send a draft order (request becomes ORDERED)")
    public ResponseEntity<OrderResponse> send(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        PurchaseOrder order = orders.send(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.ok(OrderResponse.from(order, orders.lines(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Record goods receipt per line (over-receipt is rejected)")
    public ResponseEntity<OrderResponse> receive(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveRequest request) {
        PurchaseOrder order = orders.receive(
                principal.tenantId(),
                principal.userId(),
                id,
                request.lines().stream()
                        .map(line -> new OrderService.Receipt(line.itemId(), line.quantity()))
                        .toList());
        return ResponseEntity.ok(OrderResponse.from(order, orders.lines(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Close a fully received order")
    public ResponseEntity<OrderResponse> close(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        PurchaseOrder order = orders.close(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.ok(OrderResponse.from(order, orders.lines(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Cancel a draft or sent order")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        PurchaseOrder order = orders.cancel(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.ok(OrderResponse.from(order, orders.lines(principal.tenantId(), id)));
    }

    @GetMapping("/{id}/document.pdf")
    @PreAuthorize("hasAuthority('order:read')")
    @Operation(summary = "Printable purchase order (supplier, lines, total)")
    public ResponseEntity<byte[]> document(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        byte[] content = documents.document(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.pdf("purchase-order", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize("hasAuthority('order:read')")
    @Operation(summary = "Export filtered orders as xlsx (max 5000 rows)")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) PurchaseOrder.Status status) {
        List<List<Object>> body = documents
                .exportRows(principal.tenantId(), status, principal.userId())
                .stream()
                .map(row -> List.<Object>of(
                        row.id().toString(),
                        row.supplier(),
                        row.request(),
                        row.status(),
                        row.currency(),
                        row.totalMinor() / 100.0,
                        row.createdAt()))
                .toList();
        byte[] content = Spreadsheet.book(
                "Orders",
                List.of("reference", "supplier", "request", "status", "currency", "total", "created_at"),
                body);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.xlsx("purchase-orders", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
