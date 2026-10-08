package com.procureflow.purchaseorder.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.procurement.application.OrderSourcePort;
import com.procureflow.purchaseorder.domain.OrderItem;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import com.procureflow.purchaseorder.infrastructure.OrderItemRepository;
import com.procureflow.purchaseorder.infrastructure.PurchaseOrderRepository;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.application.SupplierLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier orders born from approved requests. Creation snapshots the
 * request lines; sending flips the request to ORDERED in the same
 * transaction. Receipt accumulates per line and derives the status, so a
 * partial delivery reads PARTIALLY_RECEIVED until every line is full.
 */
@Service
public class OrderService {

    private final PurchaseOrderRepository orders;
    private final OrderItemRepository lines;
    private final OrderSourcePort requests;
    private final SupplierLookup suppliers;
    private final TenantProvisioning tenants;

    public OrderService(
            PurchaseOrderRepository orders,
            OrderItemRepository lines,
            OrderSourcePort requests,
            SupplierLookup suppliers,
            TenantProvisioning tenants) {
        this.orders = orders;
        this.lines = lines;
        this.requests = requests;
        this.suppliers = suppliers;
        this.tenants = tenants;
    }

    @Transactional
    public PurchaseOrder create(String tenantSlug, UUID requestId, UUID supplierId) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (!suppliers.existsInTenant(supplierId, tenantSlug)) {
            throw ApiException.notFound("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        OrderSourcePort.Snapshot snapshot = requests.snapshot(tenantSlug, requestId);
        if (!"APPROVED".equals(snapshot.status())) {
            throw ApiException.conflict("NOT_APPROVED", "Only approved requests become orders");
        }
        if (orders.existsByRequestId(requestId)) {
            throw ApiException.conflict("ALREADY_ORDERED", "This request already has an order");
        }
        try {
            PurchaseOrder order = new PurchaseOrder(tenantId, requestId, supplierId, snapshot.currency());
            orders.saveAndFlush(order);
            for (OrderSourcePort.Line line : snapshot.lines()) {
                lines.save(new OrderItem(
                        order,
                        line.description(),
                        line.category(),
                        line.quantity(),
                        line.unitPriceMinor(),
                        line.currency()));
            }
            return order;
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_ORDERED", "This request already has an order");
        }
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list(String tenantSlug, PurchaseOrder.Status status) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (status == null) {
            return orders.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return orders.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(String tenantSlug, UUID id) {
        return scoped(tenantSlug, id);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> lines(String tenantSlug, UUID id) {
        scoped(tenantSlug, id);
        return lines.findAllByOrder_IdOrderByCreatedAt(id);
    }

    @Transactional
    public PurchaseOrder send(String tenantSlug, UUID id) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        PurchaseOrder order = orders
                .lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Purchase order not found"));
        if (order.getStatus() != PurchaseOrder.Status.DRAFT) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only draft orders can be sent");
        }
        order.setStatus(PurchaseOrder.Status.SENT);
        orders.save(order);
        requests.markOrdered(tenantSlug, order.getRequestId());
        return order;
    }

    @Transactional
    public PurchaseOrder receive(String tenantSlug, UUID id, List<Receipt> receipts) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        PurchaseOrder order = orders
                .lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Purchase order not found"));
        if (order.getStatus() != PurchaseOrder.Status.SENT
                && order.getStatus() != PurchaseOrder.Status.PARTIALLY_RECEIVED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only sent orders receive goods");
        }
        if (receipts == null || receipts.isEmpty()) {
            throw ApiException.badRequest("EMPTY_RECEIPT", "At least one received line is required");
        }
        for (Receipt receipt : receipts) {
            OrderItem line = lines
                    .findByIdAndOrderId(receipt.itemId(), order.getId())
                    .orElseThrow(() -> ApiException.notFound("ORDER_ITEM_NOT_FOUND", "Order line not found"));
            if (receipt.quantity() < 1) {
                throw ApiException.badRequest("INVALID_QTY", "Received quantity must be positive");
            }
            if (line.getReceivedQty() + receipt.quantity() > line.getQuantity()) {
                throw ApiException.conflict("OVER_RECEIPT", "Cannot receive more than ordered");
            }
            line.receive(receipt.quantity());
            lines.save(line);
        }
        boolean full = lines.findAllByOrder_IdOrderByCreatedAt(order.getId()).stream()
                .allMatch(OrderItem::isFullyReceived);
        order.setStatus(full ? PurchaseOrder.Status.RECEIVED : PurchaseOrder.Status.PARTIALLY_RECEIVED);
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrder close(String tenantSlug, UUID id) {
        PurchaseOrder order = scopedForWrite(tenantSlug, id);
        if (order.getStatus() != PurchaseOrder.Status.RECEIVED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only received orders can be closed");
        }
        order.setStatus(PurchaseOrder.Status.CLOSED);
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrder cancel(String tenantSlug, UUID id) {
        PurchaseOrder order = scopedForWrite(tenantSlug, id);
        if (order.getStatus() != PurchaseOrder.Status.DRAFT && order.getStatus() != PurchaseOrder.Status.SENT) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only draft or sent orders can be cancelled");
        }
        order.setStatus(PurchaseOrder.Status.CANCELLED);
        return orders.save(order);
    }

    private PurchaseOrder scoped(String tenantSlug, UUID id) {
        return orders
                .findByIdAndTenantId(id, tenants.requireTenantId(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Purchase order not found"));
    }

    private PurchaseOrder scopedForWrite(String tenantSlug, UUID id) {
        return orders
                .lockByIdAndTenantId(id, tenants.requireTenantId(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Purchase order not found"));
    }

    public record Receipt(UUID itemId, int quantity) {
    }
}
