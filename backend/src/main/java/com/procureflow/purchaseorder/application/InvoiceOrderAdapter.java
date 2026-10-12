package com.procureflow.purchaseorder.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import com.procureflow.purchaseorder.infrastructure.OrderItemRepository;
import com.procureflow.purchaseorder.infrastructure.PurchaseOrderRepository;
import com.procureflow.shared.web.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link InvoiceOrderPort} over the purchaseorder module's own repositories. */
@Service
public class InvoiceOrderAdapter implements InvoiceOrderPort {

    private final PurchaseOrderRepository orders;
    private final OrderItemRepository lines;
    private final TenantProvisioning tenants;

    public InvoiceOrderAdapter(
            PurchaseOrderRepository orders, OrderItemRepository lines, TenantProvisioning tenants) {
        this.orders = orders;
        this.lines = lines;
        this.tenants = tenants;
    }

    @Override
    @Transactional
    public LockedOrder lockForInvoicing(String tenantSlug, UUID orderId) {
        PurchaseOrder order = orders
                .lockByIdAndTenantId(orderId, tenants.requireTenantId(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Purchase order not found"));
        return view(order);
    }

    @Override
    @Transactional(readOnly = true)
    public LockedOrder orderView(String tenantSlug, UUID orderId) {
        PurchaseOrder order = orders
                .findByIdAndTenantId(orderId, tenants.requireTenantId(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Purchase order not found"));
        return view(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LockedOrder> orderViews(String tenantSlug, Set<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        Map<UUID, PurchaseOrder> found = new HashMap<>();
        for (PurchaseOrder order : orders.findAllById(orderIds)) {
            if (order.getTenantId().equals(tenantId)) {
                found.put(order.getId(), order);
            }
        }
        if (found.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Line>> byOrder = new HashMap<>();
        for (var line : lines.findAllByOrder_IdInOrderByOrder_IdAscCreatedAtAsc(found.keySet())) {
            byOrder
                    .computeIfAbsent(line.getOrder().getId(), id -> new ArrayList<>())
                    .add(describe(line));
        }
        Map<UUID, LockedOrder> views = new HashMap<>();
        for (PurchaseOrder order : found.values()) {
            views.put(
                    order.getId(),
                    new LockedOrder(
                            order.getId(),
                            order.getSupplierId(),
                            order.getStatus().name(),
                            order.getCurrency(),
                            byOrder.getOrDefault(order.getId(), List.of())));
        }
        return views;
    }

    private LockedOrder view(PurchaseOrder order) {
        List<Line> snapshot = lines.findAllByOrder_IdOrderByCreatedAt(order.getId()).stream()
                .map(InvoiceOrderAdapter::describe)
                .toList();
        return new LockedOrder(
                order.getId(), order.getSupplierId(), order.getStatus().name(), order.getCurrency(), snapshot);
    }

    private static Line describe(com.procureflow.purchaseorder.domain.OrderItem item) {
        return new Line(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPriceMinor(),
                item.getCurrency(),
                item.getReceivedQty());
    }
}
