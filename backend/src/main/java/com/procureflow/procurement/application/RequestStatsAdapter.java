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
        long requested = 0;
        for (Object[] row : items.sumActiveByCategoryAndCurrency(tenantSlug)) {
            String category = row[0] == null || row[0].toString().isBlank() ? "Uncategorized" : row[0].toString();
            String currency = row[1] == null ? "" : row[1].toString();
            long amount = (Long) row[2];
            categories.add(new CategoryTotal(category, currency, amount));
            requested += amount;
        }
        categories.sort((a, b) -> Long.compare(b.amountMinor(), a.amountMinor()));
        return new RequestTotals(requested, categories);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalSummary approvalSummary(String tenantSlug) {
        List<Object[]> rows = requests.approvalSummaryByTenantSlug(tenantSlug);
        Object[] row = rows.isEmpty() ? new Object[] {0L, 0L, null, null} : rows.get(0);
        return new ApprovalSummary(
                row[0] == null ? 0L : ((Number) row[0]).longValue(),
                row[1] == null ? 0L : ((Number) row[1]).longValue(),
                row[2] == null ? null : ((Number) row[2]).doubleValue(),
                row[3] == null ? null : ((Number) row[3]).doubleValue());
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
