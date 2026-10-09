package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link RequestStatsPort} over procurement's own repository. */
@Service
public class RequestStatsAdapter implements RequestStatsPort {

    private final PurchaseRequestRepository requests;

    public RequestStatsAdapter(PurchaseRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestStat> requestStats(String tenantSlug) {
        return requests.findAllByTenantSlug(tenantSlug).stream()
                .map(RequestStatsAdapter::stat)
                .toList();
    }

    private static RequestStat stat(PurchaseRequest request) {
        List<ItemStat> items = request.getItems() == null
                ? List.of()
                : request.getItems().stream()
                        .map(i -> new ItemStat(
                                i.getCategory(), i.getQuantity(), i.getUnitPriceMinor(), i.getCurrency()))
                        .toList();
        String currency = items.isEmpty() ? "MAD" : items.get(0).currency();
        return new RequestStat(
                request.getId(),
                request.getStatus().name(),
                currency,
                request.getCreatedAt(),
                request.getSubmittedAt(),
                request.getDecidedAt(),
                items);
    }
}
