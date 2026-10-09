package com.procureflow.purchaseorder.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import com.procureflow.purchaseorder.infrastructure.OrderItemRepository;
import com.procureflow.purchaseorder.infrastructure.PurchaseOrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link OrderStatsPort} over the purchaseorder module's own repositories. */
@Service
public class OrderStatsAdapter implements OrderStatsPort {

    private final PurchaseOrderRepository orders;
    private final OrderItemRepository lines;
    private final TenantProvisioning tenants;

    public OrderStatsAdapter(
            PurchaseOrderRepository orders, OrderItemRepository lines, TenantProvisioning tenants) {
        this.orders = orders;
        this.lines = lines;
        this.tenants = tenants;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStat> orderStats(String tenantSlug) {
        return orders.findAllByTenantIdOrderByCreatedAtDesc(tenants.requireTenantId(tenantSlug)).stream()
                .map(this::stat)
                .toList();
    }

    private OrderStat stat(PurchaseOrder order) {
        List<LineStat> snapshot = lines.findAllByOrder_IdOrderByCreatedAt(order.getId()).stream()
                .map(i -> new LineStat(i.getQuantity(), i.getUnitPriceMinor(), i.getCurrency(), i.getReceivedQty()))
                .toList();
        return new OrderStat(order.getId(), order.getStatus().name(), order.getCurrency(), order.getCreatedAt(),
                snapshot);
    }
}
