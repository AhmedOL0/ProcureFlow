package com.procureflow.purchaseorder.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.purchaseorder.domain.OrderItem;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import com.procureflow.purchaseorder.infrastructure.OrderItemRepository;
import com.procureflow.purchaseorder.infrastructure.PurchaseOrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        List<PurchaseOrder> found = orders.findAllByTenantIdOrderByCreatedAtDesc(tenants.requireTenantId(tenantSlug));
        return statsOf(found);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStat> orderStats(String tenantSlug, Instant from, Instant to) {
        List<PurchaseOrder> found =
                orders.findAllByTenantIdAndCreatedAtBetween(tenants.requireTenantId(tenantSlug), from, to);
        return statsOf(found);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatsPort.OrderTotal> orderTotals(String tenantSlug) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        Map<UUID, Long> sums = new HashMap<>();
        for (Object[] row : lines.sumByTenantId(tenantId)) {
            sums.put((UUID) row[0], (Long) row[1]);
        }
        List<OrderStatsPort.OrderTotal> totals = new ArrayList<>();
        for (PurchaseOrder order : orders.findAllByTenantIdOrderByCreatedAtDesc(tenantId)) {
            totals.add(new OrderStatsPort.OrderTotal(
                    order.getId(),
                    order.getStatus().name(),
                    order.getCurrency(),
                    order.getCreatedAt(),
                    sums.getOrDefault(order.getId(), 0L)));
        }
        return totals;
    }

    private List<OrderStat> statsOf(List<PurchaseOrder> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        // One fetch for every order's lines, not one query per order.
        Map<UUID, List<LineStat>> linesByOrder = new HashMap<>();
        for (OrderItem line : lines.findAllByOrder_IdInOrderByOrder_IdAscCreatedAtAsc(
                found.stream().map(PurchaseOrder::getId).toList())) {
            linesByOrder
                    .computeIfAbsent(line.getOrder().getId(), id -> new ArrayList<>())
                    .add(new LineStat(line.getQuantity(), line.getUnitPriceMinor(), line.getCurrency(),
                            line.getReceivedQty()));
        }
        return found.stream()
                .map(order -> new OrderStat(
                        order.getId(),
                        order.getStatus().name(),
                        order.getCurrency(),
                        order.getCreatedAt(),
                        linesByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }
}
