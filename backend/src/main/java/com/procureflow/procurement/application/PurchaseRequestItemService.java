package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.domain.PurchaseRequestItem;
import com.procureflow.procurement.infrastructure.PurchaseRequestItemRepository;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.application.SupplierLookup;
import com.procureflow.supplier.domain.Supplier;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direct item edits. Items live and die with draft requests: anything past
 * DRAFT is immutable until the approval workflow (Phase 4) takes over.
 */
@Service
@Transactional
public class PurchaseRequestItemService {

    private final PurchaseRequestItemRepository items;
    private final SupplierLookup supplierLookup;
    private final EntityManager entities;

    public PurchaseRequestItemService(
            PurchaseRequestItemRepository items,
            SupplierLookup supplierLookup,
            EntityManager entities) {
        this.items = items;
        this.supplierLookup = supplierLookup;
        this.entities = entities;
    }

    /**
     * Persists one item for an already-loaded request. Joins the caller's
     * transaction (creation batches several of these atomically).
     */
    public PurchaseRequestItem createForRequest(
            PurchaseRequest request, String tenantSlug, PurchaseRequestService.ItemInput input) {
        return items.save(toItem(request, tenantSlug, input));
    }

    public PurchaseRequestItem update(
            String tenantSlug, UUID itemId, UUID callerId, boolean admin,
            String description, String category, Integer quantity, Long unitPriceMinor,
            String currency, UUID supplierId) {
        PurchaseRequestItem item = scopedDraftItem(tenantSlug, itemId, callerId, admin);
        if (description != null) {
            item.setDescription(description);
        }
        if (category != null) {
            item.setCategory(category);
        }
        if (quantity != null) {
            item.setQuantity(quantity);
        }
        if (unitPriceMinor != null) {
            item.setUnitPriceMinor(unitPriceMinor);
        }
        if (currency != null) {
            item.setCurrency(currency);
        }
        if (supplierId != null) {
            if (!supplierLookup.existsInTenant(supplierId, tenantSlug)) {
                throw ApiException.notFound("SUPPLIER_NOT_FOUND", "Supplier not found");
            }
            item.setSupplier(entities.getReference(Supplier.class, supplierId));
        }
        return items.save(item);
    }

    public void remove(String tenantSlug, UUID itemId, UUID callerId, boolean admin) {
        items.delete(scopedDraftItem(tenantSlug, itemId, callerId, admin));
    }

    private PurchaseRequestItem toItem(
            PurchaseRequest request, String tenantSlug, PurchaseRequestService.ItemInput input) {
        PurchaseRequestItem item = new PurchaseRequestItem(
                request, input.description(), input.quantity(), input.unitPriceMinor());
        item.setCategory(input.category());
        item.setCurrency(input.currency() == null ? "MAD" : input.currency());
        if (input.supplierId() != null) {
            if (!supplierLookup.existsInTenant(input.supplierId(), tenantSlug)) {
                throw ApiException.notFound("SUPPLIER_NOT_FOUND", "Supplier not found");
            }
            item.setSupplier(entities.getReference(Supplier.class, input.supplierId()));
        }
        return item;
    }

    private PurchaseRequestItem scopedDraftItem(String tenantSlug, UUID itemId, UUID callerId, boolean admin) {
        PurchaseRequestItem item = items
                .findByIdAndTenantSlug(itemId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Request item not found"));
        if (item.getRequest().getStatus() != com.procureflow.procurement.domain.PurchaseRequest.Status.DRAFT) {
            throw ApiException.conflict("NOT_DRAFT", "Only draft requests can be changed");
        }
        boolean owner = item.getRequest().getRequester().getId().equals(callerId);
        if (!admin && !owner) {
            throw ApiException.forbidden("NOT_OWNER", "Only the requester or a workspace admin can do this");
        }
        return item;
    }
}
