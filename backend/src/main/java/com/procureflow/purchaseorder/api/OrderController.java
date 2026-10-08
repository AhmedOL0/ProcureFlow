package com.procureflow.purchaseorder.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.purchaseorder.application.OrderService;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
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
 * Supplier orders. Creation needs an APPROVED request (lines snapshotted,
 * supplier mandatory); sending flips the request to ORDERED in the same
 * transaction. Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "purchase-orders", description = "Orders from approved requests through receipt")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Start a draft order from one approved request")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateOrderRequest request) {
        PurchaseOrder order = orders.create(principal.tenantId(), request.requestId(), request.supplierId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order, orders.lines(principal.tenantId(), order.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    @Operation(summary = "List orders of the current workspace, newest first")
    public ResponseEntity<List<OrderResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) PurchaseOrder.Status status) {
        // Null status lists everything; the repository branches so Hibernate
        // never sees a null parameter (see gotcha 9).
        return ResponseEntity.ok(orders.list(principal.tenantId(), status).stream()
                .map(order -> OrderResponse.from(order, orders.lines(principal.tenantId(), order.getId())))
                .toList());
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
        PurchaseOrder order = orders.send(principal.tenantId(), id);
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
        PurchaseOrder order = orders.close(principal.tenantId(), id);
        return ResponseEntity.ok(OrderResponse.from(order, orders.lines(principal.tenantId(), id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:write')")
    @Operation(summary = "Cancel a draft or sent order")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        PurchaseOrder order = orders.cancel(principal.tenantId(), id);
        return ResponseEntity.ok(OrderResponse.from(order, orders.lines(principal.tenantId(), id)));
    }
}
