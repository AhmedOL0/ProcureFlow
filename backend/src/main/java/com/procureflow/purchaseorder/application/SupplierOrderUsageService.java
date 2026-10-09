package com.procureflow.purchaseorder.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.purchaseorder.infrastructure.PurchaseOrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link SupplierOrderUsage} over the purchaseorder module's own repository. */
@Service
public class SupplierOrderUsageService implements SupplierOrderUsage {

    private final PurchaseOrderRepository orders;
    private final TenantProvisioning tenants;

    public SupplierOrderUsageService(PurchaseOrderRepository orders, TenantProvisioning tenants) {
        this.orders = orders;
        this.tenants = tenants;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(String tenantSlug, UUID supplierId) {
        return orders.existsByTenantIdAndSupplierId(tenants.requireTenantId(tenantSlug), supplierId);
    }
}
