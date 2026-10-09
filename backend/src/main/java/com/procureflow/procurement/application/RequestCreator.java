package com.procureflow.procurement.application;

import com.procureflow.identity.domain.User;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts one purchase request with its initial items in an isolated
 * transaction. Isolation is what makes the idempotency race safe: when two
 * requests with the same key arrive together, the loser's transaction dies
 * on the unique constraint while the winner's survives, and the orchestrator
 * turns the loser into a replay instead of an error.
 */
@Service
public class RequestCreator {

    private final PurchaseRequestRepository requests;
    private final PurchaseRequestItemService itemService;
    private final TenantProvisioning tenants;
    private final EntityManager entities;

    public RequestCreator(
            PurchaseRequestRepository requests,
            PurchaseRequestItemService itemService,
            TenantProvisioning tenants,
            EntityManager entities) {
        this.requests = requests;
        this.itemService = itemService;
        this.tenants = tenants;
        this.entities = entities;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurchaseRequest create(
            String tenantSlug, UUID requesterId, String key, String title, String description,
            PurchaseRequest.Priority priority, List<PurchaseRequestService.ItemInput> initialItems) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        PurchaseRequest request = new PurchaseRequest(
                entities.getReference(Tenant.class, tenantId),
                entities.getReference(User.class, requesterId),
                title,
                key);
        request.setDescription(description);
        if (priority != null) {
            request.setPriority(priority);
        }
        requests.save(request);
        if (initialItems != null) {
            for (PurchaseRequestService.ItemInput input : initialItems) {
                itemService.createForRequest(request, tenantSlug, input);
            }
        }
        return requests
                .findByIdAndTenantSlug(request.getId(), tenantSlug)
                .orElseThrow(() -> new IllegalStateException("Request vanished right after insert"));
    }
}
