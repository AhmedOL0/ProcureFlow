package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.domain.PurchaseRequestItem;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link RequestDecisionPort} over procurement's own repository. */
@Service
public class RequestDecisionAdapter implements RequestDecisionPort {

    private final PurchaseRequestRepository requests;

    public RequestDecisionAdapter(PurchaseRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional
    public LockedRequest lock(String tenantSlug, UUID requestId) {
        PurchaseRequest request = requests
                .lockByIdAndTenantSlug(requestId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Purchase request not found"));
        long total = request.getItems() == null
                ? 0L
                : request.getItems().stream().mapToLong(PurchaseRequestItem::lineTotalMinor).sum();
        return new LockedRequest(request.getId(), request.getStatus().name(), request.getRequester().getId(), total);
    }

    @Override
    @Transactional
    public void applyDecision(String tenantSlug, UUID requestId, boolean approved) {
        PurchaseRequest request = requests
                .lockByIdAndTenantSlug(requestId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Purchase request not found"));
        request.setStatus(approved ? PurchaseRequest.Status.APPROVED : PurchaseRequest.Status.REJECTED);
        request.setDecidedAt(Instant.now());
        requests.save(request);
    }
}
