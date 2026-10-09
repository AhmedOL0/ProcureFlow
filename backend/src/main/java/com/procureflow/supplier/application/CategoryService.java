package com.procureflow.supplier.application;

import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.domain.SupplierCategory;
import com.procureflow.supplier.infrastructure.SupplierCategoryRepository;
import com.procureflow.supplier.infrastructure.SupplierRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped category catalog. A category linked to any supplier cannot
 * be deleted: the explicit 409 beats a raw constraint violation.
 */
@Service
@Transactional
public class CategoryService {

    private final SupplierCategoryRepository categories;
    private final SupplierRepository suppliers;
    private final TenantProvisioning tenants;
    private final EntityManager entities;

    public CategoryService(
            SupplierCategoryRepository categories,
            SupplierRepository suppliers,
            TenantProvisioning tenants,
            EntityManager entities) {
        this.categories = categories;
        this.suppliers = suppliers;
        this.tenants = tenants;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<SupplierCategory> list(String tenantSlug) {
        return categories.findAllByTenantSlug(tenantSlug);
    }

    public SupplierCategory create(String tenantSlug, String name, String description) {
        if (categories.existsByTenantSlugAndName(tenantSlug, name)) {
            throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
        }
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        SupplierCategory category = new SupplierCategory(entities.getReference(Tenant.class, tenantId), name);
        category.setDescription(description);
        return categories.save(category);
    }

    public SupplierCategory rename(String tenantSlug, UUID id, String name, String description) {
        SupplierCategory category = scoped(tenantSlug, id);
        if (!category.getName().equals(name) && categories.existsByTenantSlugAndName(tenantSlug, name)) {
            throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
        }
        category.setName(name);
        category.setDescription(description);
        return categories.save(category);
    }

    public void delete(String tenantSlug, UUID id) {
        SupplierCategory category = scoped(tenantSlug, id);
        if (suppliers.countByCategories_Id(id) > 0) {
            throw ApiException.conflict("CATEGORY_IN_USE", "Category is assigned to suppliers; unassign it first");
        }
        categories.delete(category);
    }

    private SupplierCategory scoped(String tenantSlug, UUID id) {
        return categories
                .findByIdAndTenantSlug(id, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found"));
    }
}
