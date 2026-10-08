package com.procureflow.purchaseorder.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import com.procureflow.purchaseorder.infrastructure.OrderItemRepository;
import com.procureflow.purchaseorder.infrastructure.PurchaseOrderRepository;
import com.procureflow.shared.web.ApiException;
import java.util.List;
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

    private LockedOrder view(PurchaseOrder order) {
        List<Line> snapshot = lines.findAllByOrder_IdOrderByCreatedAt(order.getId()).stream()
                .map(i -> new Line(i.getId(), i.getQuantity(), i.getUnitPriceMinor(), i.getCurrency(),
                        i.getReceivedQty()))
                .toList();
        return new LockedOrder(order.getId(), order.getStatus().name(), order.getCurrency(), snapshot);
    }
}
