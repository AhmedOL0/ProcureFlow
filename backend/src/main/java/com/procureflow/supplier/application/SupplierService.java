package com.procureflow.supplier.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.purchaseorder.application.SupplierOrderUsage;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.ApiException;
import com.procureflow.shared.web.Paged;
import com.procureflow.supplier.domain.Supplier;
import com.procureflow.supplier.domain.SupplierCategory;
import com.procureflow.supplier.infrastructure.SupplierCategoryRepository;
import com.procureflow.supplier.infrastructure.SupplierRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier lifecycle, always scoped to the caller's tenant. Deletes consult
 * the purchase-order reference guard first and answer 409 while an order
 * points at the supplier; other links (request items) still surface through
 * the constraint fallback below.
 */
@Service
@Transactional
public class SupplierService {

    private final SupplierRepository suppliers;
    private final SupplierCategoryRepository categories;
    private final TenantProvisioning tenants;
    private final SupplierOrderUsage orderUsage;
    private final EntityManager entities;
    private final ApplicationEventPublisher events;

    public SupplierService(
            SupplierRepository suppliers,
            SupplierCategoryRepository categories,
            TenantProvisioning tenants,
            SupplierOrderUsage orderUsage,
            EntityManager entities,
            ApplicationEventPublisher events) {
        this.suppliers = suppliers;
        this.categories = categories;
        this.tenants = tenants;
        this.orderUsage = orderUsage;
        this.entities = entities;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Paged<Supplier> page(
            String tenantSlug, Supplier.Status status, String query, int page, int size) {
        String terms = query == null || query.isBlank() ? "" : query;
        PageRequest pageable = PageRequest.of(page, size);
        Page<Supplier> found = status == null
                ? suppliers.findPageByTenantSlug(tenantSlug, terms, pageable)
                : suppliers.findPageByTenantSlugAndStatus(tenantSlug, status, terms, pageable);
        return Paged.of(found.getContent(), page, size, found.getTotalElements());
    }

    /**
     * Full filtered list for xlsx export. Counts first so oversized
     * workspaces fail with EXPORT_TOO_LARGE instead of OOMing the
     * workbook; the same filters as {@link #page} apply.
     */
    @Transactional(readOnly = true)
    public List<Supplier> exportRows(String tenantSlug, Supplier.Status status, String query, UUID actorId) {
        String terms = query == null || query.isBlank() ? "" : query;
        long total = status == null
                ? suppliers.findPageByTenantSlug(tenantSlug, terms, PageRequest.of(0, 1)).getTotalElements()
                : suppliers.findPageByTenantSlugAndStatus(tenantSlug, status, terms, PageRequest.of(0, 1))
                        .getTotalElements();
        if (total > Spreadsheet.MAX_EXPORT_ROWS) {
            throw ApiException.badRequest(
                    "EXPORT_TOO_LARGE",
                    "Export is limited to " + Spreadsheet.MAX_EXPORT_ROWS + " rows; narrow the filters",
                    Map.of("matching", String.valueOf(total)));
        }
        if (total == 0) {
            return List.of();
        }
        PageRequest all = PageRequest.of(0, (int) total);
        Page<Supplier> found = status == null
                ? suppliers.findPageByTenantSlug(tenantSlug, terms, all)
                : suppliers.findPageByTenantSlugAndStatus(tenantSlug, status, terms, all);
        // Controllers map detached entities (OSIV is off): touch the lazy
        // category set now, inside the transaction.
        found.getContent().forEach(supplier -> supplier.getCategories().size());
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "supplier",
                null,
                Map.of(),
                Map.of("format", "xlsx", "rows", found.getContent().size())));
        return found.getContent();
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String tenantSlug, String name) {
        return suppliers.existsByTenantSlugAndName(tenantSlug, name);
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
        try {
            Supplier created = suppliers.save(supplier);
            // Fail inside the use case, not at commit: concurrent duplicate
            // creates race the pre-check above, and the unique net is the
            // only constraint on this table, so any violation is the name.
            suppliers.flush();
            return created;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict("SUPPLIER_EXISTS", "A supplier with this name already exists");
        }
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
        try {
            Supplier saved = suppliers.save(supplier);
            suppliers.flush();
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict("SUPPLIER_EXISTS", "A supplier with this name already exists");
        }
    }

    public void delete(String tenantSlug, UUID id) {
        if (orderUsage.isReferenced(tenantSlug, id)) {
            throw ApiException.conflict(
                    "SUPPLIER_REFERENCED", "Supplier is referenced by purchase orders and cannot be deleted");
        }
        try {
            suppliers.delete(scoped(tenantSlug, id));
            suppliers.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict(
                    "SUPPLIER_REFERENCED", "Supplier is referenced by purchase documents and cannot be deleted");
        }
    }

    public List<SupplierCategory> assignCategories(String tenantSlug, UUID supplierId, Set<UUID> categoryIds) {
        Supplier supplier = scoped(tenantSlug, supplierId);
        // One fetch for the whole set, not one query per id. Missing ids
        // report deterministically (sorted) instead of HashSet order.
        Map<UUID, SupplierCategory> found = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (SupplierCategory category : categories.findAllByIdInAndTenantSlug(categoryIds, tenantSlug)) {
                found.put(category.getId(), category);
            }
        }
        Set<SupplierCategory> assigned = new HashSet<>();
        for (UUID categoryId : categoryIds.stream().sorted().toList()) {
            SupplierCategory category = found.get(categoryId);
            if (category == null) {
                throw ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found: " + categoryId);
            }
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
