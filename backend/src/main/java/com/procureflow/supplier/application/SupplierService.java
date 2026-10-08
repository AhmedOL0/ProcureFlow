package com.procureflow.supplier.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.domain.Supplier;
import com.procureflow.supplier.domain.SupplierCategory;
import com.procureflow.supplier.infrastructure.SupplierCategoryRepository;
import com.procureflow.supplier.infrastructure.SupplierRepository;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier lifecycle, always scoped to the caller's tenant. Deletes are
 * direct for now: no referencing tables exist yet. The purchase-order epic
 * adds a reference guard here before orders can point at suppliers.
 */
@Service
@Transactional
public class SupplierService {

    private final SupplierRepository suppliers;
    private final SupplierCategoryRepository categories;
    private final TenantProvisioning tenants;
    private final EntityManager entities;

    public SupplierService(
            SupplierRepository suppliers,
            SupplierCategoryRepository categories,
            TenantProvisioning tenants,
            EntityManager entities) {
        this.suppliers = suppliers;
        this.categories = categories;
        this.tenants = tenants;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<Supplier> search(String tenantSlug, Supplier.Status status, String query) {
        String terms = query == null || query.isBlank() ? "" : query;
        if (status == null) {
            return suppliers.findAllByTenantSlug(tenantSlug, terms);
        }
        return suppliers.searchByStatus(tenantSlug, status, terms);
    }

    public Supplier create(
            String tenantSlug,
            String name,
            String taxId,
            String email,
            String phone,
            String address) {
        if (suppliers.existsByTenantSlugAndName(tenantSlug, name)) {
            throw ApiException.conflict("SUPPLIER_EXISTS", "A supplier with this name already exists");
        }
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        Supplier supplier = new Supplier(entities.getReference(Tenant.class, tenantId), name);
        supplier.setTaxId(taxId);
        supplier.setEmail(email);
        supplier.setPhone(phone);
        supplier.setAddress(address);
        return suppliers.save(supplier);
    }

    @Transactional(readOnly = true)
    public Supplier get(String tenantSlug, UUID id) {
        return scoped(tenantSlug, id);
    }

    public Supplier update(
            String tenantSlug,
            UUID id,
            String name,
            String taxId,
            String email,
            String phone,
            String address,
            Supplier.Status status) {
        Supplier supplier = scoped(tenantSlug, id);
        if (name != null && !name.equals(supplier.getName())) {
            if (suppliers.existsByTenantSlugAndName(tenantSlug, name)) {
                throw ApiException.conflict("SUPPLIER_EXISTS", "A supplier with this name already exists");
            }
            supplier.setName(name);
        }
        if (taxId != null) {
            supplier.setTaxId(taxId);
        }
        if (email != null) {
            supplier.setEmail(email);
        }
        if (phone != null) {
            supplier.setPhone(phone);
        }
        if (address != null) {
            supplier.setAddress(address);
        }
        if (status != null) {
            supplier.setStatus(status);
        }
        return suppliers.save(supplier);
    }

    public void delete(String tenantSlug, UUID id) {
        suppliers.delete(scoped(tenantSlug, id));
    }

    public List<SupplierCategory> assignCategories(String tenantSlug, UUID supplierId, Set<UUID> categoryIds) {
        Supplier supplier = scoped(tenantSlug, supplierId);
        Set<SupplierCategory> assigned = new HashSet<>();
        for (UUID categoryId : categoryIds) {
            SupplierCategory category = categories
                    .findByIdAndTenantSlug(categoryId, tenantSlug)
                    .orElseThrow(
                            () -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found: " + categoryId));
            assigned.add(category);
        }
        supplier.getCategories().clear();
        supplier.getCategories().addAll(assigned);
        suppliers.save(supplier);
        return assigned.stream().sorted((a, b) -> a.getName().compareTo(b.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<SupplierCategory> categoriesOf(String tenantSlug, UUID supplierId) {
        Supplier supplier = scoped(tenantSlug, supplierId);
        return supplier.getCategories().stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .toList();
    }

    private Supplier scoped(String tenantSlug, UUID id) {
        return suppliers
                .findByIdAndTenantSlug(id, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "Supplier not found"));
    }
}
