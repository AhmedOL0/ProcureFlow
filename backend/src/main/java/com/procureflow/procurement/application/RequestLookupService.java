package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import com.procureflow.shared.web.ApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link RequestLookup} over the procurement module's own service. */
@Service
public class RequestLookupService implements RequestLookup {

    private final PurchaseRequestService requests;
    private final PurchaseRequestRepository repository;

    public RequestLookupService(PurchaseRequestService requests, PurchaseRequestRepository repository) {
        this.requests = requests;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RequestLookup.Card> cardInTenant(UUID requestId, String tenantSlug) {
        // Absent or foreign requests are not document failures — the line
        // falls back to the bare id. Only the 404 carrier is absorbed.
        try {
            PurchaseRequest request = requests.get(tenantSlug, requestId);
            return Optional.of(
                    new RequestLookup.Card(request.getId(), request.getTitle(), request.getStatus().name()));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, RequestLookup.Card> cardsInTenant(Set<UUID> requestIds, String tenantSlug) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RequestLookup.Card> cards = new HashMap<>();
        for (PurchaseRequest request : repository.findByIdInAndTenantSlug(requestIds, tenantSlug)) {
            cards.put(
                    request.getId(),
                    new RequestLookup.Card(request.getId(), request.getTitle(), request.getStatus().name()));
        }
        return cards;
    }
}
