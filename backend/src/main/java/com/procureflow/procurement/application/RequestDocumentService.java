package com.procureflow.procurement.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.identity.application.UserLookup;
import com.procureflow.procurement.domain.PurchaseRequest;
import com.procureflow.procurement.domain.PurchaseRequestItem;
import com.procureflow.shared.document.MoneyText;
import com.procureflow.shared.document.Pdf;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Printable request dossiers: the demand record (requester, dates, lines,
 * total) as PDF. Approval verdicts live in the approval module and stay
 * out — the dossier cites the request id so the decision trail stays
 * joinable without crossing module boundaries for entities.
 */
@Service
public class RequestDocumentService {

    private final PurchaseRequestService requests;
    private final UserLookup users;
    private final ApplicationEventPublisher events;

    public RequestDocumentService(
            PurchaseRequestService requests, UserLookup users, ApplicationEventPublisher events) {
        this.requests = requests;
        this.users = users;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public byte[] document(String tenantSlug, UUID actorId, UUID requestId) {
        PurchaseRequest request = requests.get(tenantSlug, requestId);
        UserLookup.Card requester = users.cardInTenant(request.getRequester().getId(), tenantSlug).orElse(null);
        List<PurchaseRequestItem> lines = request.getItems() == null ? List.of() : request.getItems();
        long total = lines.stream().mapToLong(PurchaseRequestItem::lineTotalMinor).sum();

        List<String> meta = new ArrayList<>();
        meta.add("Workspace: " + tenantSlug + "  |  Status: " + request.getStatus().name());
        meta.add("Priority: " + (request.getPriority() == null ? "-" : request.getPriority().name()));
        meta.add("Submitted: " + Pdf.stamp(request.getSubmittedAt()));
        meta.add("Decided: " + Pdf.stamp(request.getDecidedAt()));

        List<List<String>> parties = new ArrayList<>();
        parties.add(List.of("Requested by", requester == null ? shortId(request.getRequester().getId()) : requester.displayName()));
        if (requester != null && requester.email() != null && !requester.email().isBlank()) {
            parties.add(List.of("Email", requester.email()));
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            parties.add(List.of("Justification", request.getDescription()));
        }

        List<List<String>> body = new ArrayList<>();
        for (PurchaseRequestItem line : lines) {
            body.add(List.of(
                    orDash(line.getDescription()),
                    orDash(line.getCategory()),
                    String.valueOf(line.getQuantity()),
                    MoneyText.of(line.getUnitPriceMinor(), line.getCurrency()),
                    MoneyText.of(line.lineTotalMinor(), line.getCurrency())));
        }
        String currency = lines.isEmpty() ? "MAD" : orDash(lines.get(0).getCurrency());

        byte[] content = Pdf.document(
                "Purchase request " + shortId(request.getId()) + " — " + request.getTitle(),
                meta,
                List.of(
                        new Pdf.Section(null, new Pdf.Table(List.of(), List.of(25, 75), parties, Set.of())),
                        new Pdf.Section(
                                "Lines (" + lines.size() + ")",
                                new Pdf.Table(
                                        List.of("Description", "Category", "Qty", "Unit price", "Total"),
                                        List.of(40, 16, 8, 18, 18),
                                        body,
                                        Set.of(2, 3, 4))),
                        new Pdf.Section(
                                null,
                                new Pdf.Table(
                                        List.of(),
                                        List.of(82, 18),
                                        List.of(List.of(
                                                "Total (" + currency + ")",
                                                MoneyText.of(total, currency))),
                                        Set.of(1)))),
                "ProcureFlow purchase request",
                Instant.now());
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "purchase_request",
                request.getId(),
                Map.of(),
                Map.of("format", "pdf")));
        return content;
    }

    private static String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
