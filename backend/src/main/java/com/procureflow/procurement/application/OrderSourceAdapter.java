package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import com.procureflow.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link OrderSourcePort} over procurement's own repository. */
@Service
public class OrderSourceAdapter implements OrderSourcePort {

    private final PurchaseRequestRepository requests;

    public OrderSourceAdapter(PurchaseRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public Snapshot snapshot(String tenantSlug, UUID requestId) {
        PurchaseRequest request = requests
                .findByIdAndTenantSlug(requestId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Purchase request not found"));
        List<Line> lines = request.getItems() == null
                ? List.of()
                : request.getItems().stream()
                        .map(i -> new Line(
                                i.getDescription(),
                                i.getCategory(),
                                i.getQuantity(),
                                i.getUnitPriceMinor(),
                                i.getCurrency(),
                                i.getSupplier() == null ? null : i.getSupplier().getId()))
                        .toList();
        String currency = lines.isEmpty() ? "MAD" : lines.get(0).currency();
        return new Snapshot(request.getId(), request.getStatus().name(), currency, lines);
    }

    @Override
    @Transactional
    public void markOrdered(String tenantSlug, UUID requestId) {
        PurchaseRequest request = requests
                .lockByIdAndTenantSlug(requestId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Purchase request not found"));
        if (request.getStatus() != PurchaseRequest.Status.APPROVED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only approved requests become ordered");
        }
        request.setStatus(PurchaseRequest.Status.ORDERED);
        requests.save(request);
    }
}
