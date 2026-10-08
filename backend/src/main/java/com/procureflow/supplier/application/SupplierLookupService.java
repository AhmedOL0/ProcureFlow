package com.procureflow.supplier.application;

import com.procureflow.supplier.infrastructure.SupplierRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link SupplierLookup} over the supplier module's own repository. */
@Service
public class SupplierLookupService implements SupplierLookup {

    private final SupplierRepository suppliers;

    public SupplierLookupService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsInTenant(UUID supplierId, String tenantSlug) {
        return suppliers.findByIdAndTenantSlug(supplierId, tenantSlug).isPresent();
    }
}
