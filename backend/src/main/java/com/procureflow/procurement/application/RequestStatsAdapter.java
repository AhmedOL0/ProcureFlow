package com.procureflow.procurement.application;

import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.infrastructure.PurchaseRequestItemRepository;
import com.procureflow.procurement.infrastructure.PurchaseRequestRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link RequestStatsPort} over procurement's own repository. */
@Service
public class RequestStatsAdapter implements RequestStatsPort {

    private final PurchaseRequestRepository requests;
    private final PurchaseRequestItemRepository items;

    public RequestStatsAdapter(PurchaseRequestRepository requests, PurchaseRequestItemRepository items) {
        this.requests = requests;
        this.items = items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestStat> requestStats(String tenantSlug) {
        return requests.findAllByTenantSlug(tenantSlug).stream()
                .map(RequestStatsAdapter::stat)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestStat> requestStats(String tenantSlug, Instant from, Instant to) {
        return requests.findAllByTenantSlugAndCreatedAtBetween(tenantSlug, from, to).stream()
                .map(RequestStatsAdapter::stat)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RequestTotals requestTotals(String tenantSlug) {
        List<CategoryTotal> categories = new ArrayList<>();
        for (Object[] row : items.sumActiveByCategoryAndTenantSlug(tenantSlug)) {
            String category = row[0] == null || row[0].toString().isBlank() ? "Uncategorized" : row[0].toString();
            categories.add(new CategoryTotal(category, (Long) row[1]));
        }
        categories.sort((a, b) -> Long.compare(b.amountMinor(), a.amountMinor()));
        return new RequestTotals(items.sumActiveByTenantSlug(tenantSlug), categories);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadRow> requestLeads(String tenantSlug) {
        List<LeadRow> leads = new ArrayList<>();
        for (Object[] row : requests.leadRowsByTenantSlug(tenantSlug)) {
            leads.add(new LeadRow(
                    ((PurchaseRequest.Status) row[0]).name(), (Instant) row[1], (Instant) row[2]));
        }
        return leads;
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
