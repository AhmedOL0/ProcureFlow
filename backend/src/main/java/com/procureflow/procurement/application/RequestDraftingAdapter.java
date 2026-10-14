package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Default {@link RequestDrafting} over the procurement module's own
 * creation path: same validation, same single-currency guard, same
 * idempotency race handling as a direct POST.
 */
@Service
public class RequestDraftingAdapter implements RequestDrafting {

    private final PurchaseRequestService requests;

    public RequestDraftingAdapter(PurchaseRequestService requests) {
        this.requests = requests;
    }

    @Override
    public Creation createFromDraft(String tenantSlug, UUID requesterId, String key, String title, String priority,
            List<ItemInput> items) {
        PurchaseRequest.Priority mapped = null;
        if (priority != null) {
            try {
                mapped = PurchaseRequest.Priority.valueOf(priority);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_PRIORITY", "Priority must be LOW, MEDIUM, HIGH or URGENT");
            }
        }
        PurchaseRequestService.Creation creation = requests.creation(
                tenantSlug,
                requesterId,
                key,
                title,
                null,
                mapped,
                items == null
                        ? List.of()
                        : items.stream()
                                .map(i -> new PurchaseRequestService.ItemInput(
                                        i.description(), null, i.quantity(), i.unitPriceMinor(), null, null))
                                .toList());
        return new Creation(creation.request().getId(), creation.replayed());
    }
}
