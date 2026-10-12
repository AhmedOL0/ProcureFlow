package com.procureflow.procurement.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.identity.application.UserLookup;
import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.domain.PurchaseRequestItem;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * xlsx request exports. Totals sum the EntityGraph-loaded items in memory
 * (no per-row queries); requester names resolve through the user port in
 * one query. Same filters as the paged list.
 */
@Service
public class RequestExportService {

    public record RequestExportRow(
            String title,
            String requester,
            String status,
            String priority,
            String currency,
            long totalMinor,
            Instant submittedAt,
            Instant createdAt) {
    }

    private final PurchaseRequestService requests;
    private final UserLookup users;
    private final ApplicationEventPublisher events;

    public RequestExportService(
            PurchaseRequestService requests, UserLookup users, ApplicationEventPublisher events) {
        this.requests = requests;
        this.users = users;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<RequestExportRow> exportRows(
            String tenantSlug, PurchaseRequest.Status status, String query, UUID actorId) {
        long total = requests.page(tenantSlug, status, query, 0, 1).totalElements();
        if (total > Spreadsheet.MAX_EXPORT_ROWS) {
            throw ApiException.badRequest(
                    "EXPORT_TOO_LARGE",
                    "Export is limited to " + Spreadsheet.MAX_EXPORT_ROWS + " rows; narrow the filters",
                    Map.of("matching", String.valueOf(total)));
        }
        if (total == 0) {
            return List.of();
        }
        List<PurchaseRequest> found = requests.page(tenantSlug, status, query, 0, (int) total).content();
        Set<UUID> requesterIds =
                found.stream().map(request -> request.getRequester().getId()).collect(Collectors.toSet());
        Map<UUID, UserLookup.Card> cards = users.cardsInTenant(requesterIds, tenantSlug);
        List<RequestExportRow> rows = new ArrayList<>();
        for (PurchaseRequest request : found) {
            UserLookup.Card card = cards.get(request.getRequester().getId());
            rows.add(new RequestExportRow(
                    request.getTitle(),
                    card == null ? "" : card.displayName(),
                    request.getStatus() == null ? "" : request.getStatus().name(),
                    request.getPriority() == null ? "" : request.getPriority().name(),
                    currencyOf(request),
                    request.getItems().stream().mapToLong(PurchaseRequestItem::lineTotalMinor).sum(),
                    request.getSubmittedAt(),
                    request.getCreatedAt()));
        }
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "purchase_request",
                null,
                Map.of(),
                Map.of("format", "xlsx", "rows", rows.size())));
        return rows;
    }

    private static String currencyOf(PurchaseRequest request) {
        return request.getItems().stream()
                .findFirst()
                .map(PurchaseRequestItem::getCurrency)
                .orElse("");
    }
}
