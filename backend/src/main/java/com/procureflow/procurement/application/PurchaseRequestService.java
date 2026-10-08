package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.domain.PurchaseRequestItem;
import com.procureflow.procurement.infrastructure.PurchaseRequestItemRepository;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase request lifecycle. Creation is idempotent per tenant key: replays
 * return the original request, and the unique constraint converts a lost
 * race into a replay instead of a duplicate. A violated insert poisons its
 * transaction, so creation is orchestrated without a surrounding one:
 * pre-check, then an isolated insert attempt, then a reload. Only DRAFT
 * requests are editable; submit requires at least one valid item.
 */
@Service
public class PurchaseRequestService {

    private final PurchaseRequestRepository requests;
    private final PurchaseRequestItemRepository items;
    private final PurchaseRequestItemService itemService;
    private final RequestCreator creator;

    public PurchaseRequestService(
            PurchaseRequestRepository requests,
            PurchaseRequestItemRepository items,
            PurchaseRequestItemService itemService,
            RequestCreator creator) {
        this.requests = requests;
        this.items = items;
        this.itemService = itemService;
        this.creator = creator;
    }

    /** Creates a draft or replays the original when the key was already used. */
    public Creation creation(
            String tenantSlug, UUID requesterId, String key, String title, String description,
            PurchaseRequest.Priority priority, List<ItemInput> initialItems) {
        return requests
                .findByTenantSlugAndIdempotencyKey(tenantSlug, key)
                .map(request -> new Creation(request, true))
                .orElseGet(() -> {
                    try {
                        return new Creation(
                                creator.create(
                                        tenantSlug, requesterId, key, title, description, priority, initialItems),
                                false);
                    } catch (DataIntegrityViolationException e) {
                        return new Creation(reloadedByKey(tenantSlug, key), true);
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequest> list(String tenantSlug, PurchaseRequest.Status status) {
        if (status == null) {
            return requests.findAllByTenantSlug(tenantSlug);
        }
        return requests.findAllByTenantSlugAndStatus(tenantSlug, status);
    }

    @Transactional(readOnly = true)
    public PurchaseRequest get(String tenantSlug, UUID id) {
        return scoped(tenantSlug, id);
    }

    @Transactional
    public PurchaseRequest update(
            String tenantSlug, UUID id, UUID callerId, boolean admin,
            String title, String description, PurchaseRequest.Priority priority) {
        PurchaseRequest request = editable(tenantSlug, id, callerId, admin);
        if (title != null) {
            request.setTitle(title);
        }
        if (description != null) {
            request.setDescription(description);
        }
        if (priority != null) {
            request.setPriority(priority);
        }
        requests.save(request);
        return reloaded(id, tenantSlug);
    }

    @Transactional
    public PurchaseRequest submit(String tenantSlug, UUID id, UUID callerId, boolean admin) {
        PurchaseRequest request = editable(tenantSlug, id, callerId, admin);
        if (items.countByRequest_Id(id) == 0) {
            throw ApiException.badRequest("EMPTY_REQUEST", "A request needs at least one item before submit");
        }
        request.setStatus(PurchaseRequest.Status.SUBMITTED);
        request.setSubmittedAt(Instant.now());
        requests.save(request);
        return reloaded(id, tenantSlug);
    }

    @Transactional
    public PurchaseRequest cancel(String tenantSlug, UUID id, UUID callerId, boolean admin) {
        PurchaseRequest request = scoped(tenantSlug, id);
        if (request.getStatus() != PurchaseRequest.Status.DRAFT
                && request.getStatus() != PurchaseRequest.Status.SUBMITTED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only draft or submitted requests can be cancelled");
        }
        requireOwnerOrAdmin(request, callerId, admin);
        request.setStatus(PurchaseRequest.Status.CANCELLED);
        requests.save(request);
        return reloaded(id, tenantSlug);
    }

    @Transactional
    public void delete(String tenantSlug, UUID id, UUID callerId, boolean admin) {
        PurchaseRequest request = editable(tenantSlug, id, callerId, admin);
        requests.delete(request);
    }

    @Transactional
    public PurchaseRequestItem addItem(String tenantSlug, UUID requestId, UUID callerId, boolean admin, ItemInput input) {
        PurchaseRequest request = editable(tenantSlug, requestId, callerId, admin);
        return itemService.createForRequest(request, tenantSlug, input);
    }

    private PurchaseRequest editable(String tenantSlug, UUID id, UUID callerId, boolean admin) {
        PurchaseRequest request = scoped(tenantSlug, id);
        if (request.getStatus() != PurchaseRequest.Status.DRAFT) {
            throw ApiException.conflict("NOT_DRAFT", "Only draft requests can be changed");
        }
        requireOwnerOrAdmin(request, callerId, admin);
        return request;
    }

    private void requireOwnerOrAdmin(PurchaseRequest request, UUID callerId, boolean admin) {
        if (!admin && !request.getRequester().getId().equals(callerId)) {
            throw ApiException.forbidden("NOT_OWNER", "Only the requester or a workspace admin can do this");
        }
    }

    private PurchaseRequest scoped(String tenantSlug, UUID id) {
        return requests
                .findByIdAndTenantSlug(id, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Purchase request not found"));
    }

    private PurchaseRequest reloaded(UUID id, String tenantSlug) {
        return scoped(tenantSlug, id);
    }

    private PurchaseRequest reloadedByKey(String tenantSlug, String key) {
        return requests
                .findByTenantSlugAndIdempotencyKey(tenantSlug, key)
                .orElseThrow(() -> ApiException.conflict("DUPLICATE_REQUEST", "Request already exists"));
    }

    public record Creation(PurchaseRequest request, boolean replayed) {
    }

    public record ItemInput(
            String description, String category, int quantity, long unitPriceMinor, String currency, UUID supplierId) {
    }
}
