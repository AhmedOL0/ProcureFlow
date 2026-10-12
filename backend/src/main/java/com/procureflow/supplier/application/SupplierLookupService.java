package com.procureflow.supplier.application;

import com.procureflow.supplier.infrastructure.SupplierRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierLookup.Card> cardInTenant(UUID supplierId, String tenantSlug) {
        return suppliers
                .findByIdAndTenantSlug(supplierId, tenantSlug)
                .map(supplier -> new SupplierLookup.Card(
                        supplier.getId(), supplier.getName(), supplier.getEmail(), supplier.getAddress()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SupplierLookup.Card> cardsInTenant(Set<UUID> supplierIds, String tenantSlug) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SupplierLookup.Card> cards = new HashMap<>();
        for (var supplier : suppliers.findByIdInAndTenantSlug(supplierIds, tenantSlug)) {
            cards.put(
                    supplier.getId(),
                    new SupplierLookup.Card(
                            supplier.getId(), supplier.getName(), supplier.getEmail(), supplier.getAddress()));
        }
        return cards;
    }
}
