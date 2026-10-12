package com.procureflow.purchaseorder.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.procurement.application.RequestLookup;
import com.procureflow.purchaseorder.domain.OrderItem;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import com.procureflow.shared.document.MoneyText;
import com.procureflow.shared.document.Pdf;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.application.SupplierLookup;
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
 * Printable purchase orders and xlsx order exports. Names resolve through
 * the supplier/request ports (bulk, one query each) — entities never cross
 * modules, and cross-tenant lookups degrade to bare ids instead of failing
 * the document.
 */
@Service
public class OrderDocumentService {

    public record OrderExportRow(
            UUID id,
            String supplier,
            String request,
            String status,
            String currency,
            long totalMinor,
            Instant createdAt) {
    }

    private final OrderService orders;
    private final SupplierLookup suppliers;
    private final RequestLookup requests;
    private final ApplicationEventPublisher events;

    public OrderDocumentService(
            OrderService orders,
            SupplierLookup suppliers,
            RequestLookup requests,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.requests = requests;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public byte[] document(String tenantSlug, UUID actorId, UUID orderId) {
        PurchaseOrder order = orders.get(tenantSlug, orderId);
        List<OrderItem> lines = orders.lines(tenantSlug, orderId);
        SupplierLookup.Card vendor = suppliers.cardInTenant(order.getSupplierId(), tenantSlug).orElse(null);
        RequestLookup.Card request = requests.cardInTenant(order.getRequestId(), tenantSlug).orElse(null);
        long total = lines.stream().mapToLong(OrderItem::lineTotalMinor).sum();

        List<String> meta = new ArrayList<>();
        meta.add("Workspace: " + tenantSlug + "  |  Status: " + order.getStatus().name());
        meta.add("Created: " + Pdf.stamp(order.getCreatedAt()));
        meta.add("Request: " + (request == null ? shortId(order.getRequestId()) : request.title()));

        List<List<String>> parties = new ArrayList<>();
        parties.add(List.of("Supplier", vendor == null ? shortId(order.getSupplierId()) : vendor.name()));
        if (vendor != null) {
            parties.add(List.of("Email", orDash(vendor.email())));
            parties.add(List.of("Address", orDash(vendor.address())));
        }
        parties.add(List.of("Currency", order.getCurrency()));

        List<List<String>> body = new ArrayList<>();
        for (OrderItem line : lines) {
            body.add(List.of(
                    line.getDescription(),
                    orDash(line.getCategory()),
                    String.valueOf(line.getQuantity()),
                    MoneyText.of(line.getUnitPriceMinor(), line.getCurrency()),
                    String.valueOf(line.getReceivedQty()),
                    MoneyText.of(line.lineTotalMinor(), line.getCurrency())));
        }

        byte[] content = Pdf.document(
                "Purchase order " + shortId(order.getId()),
                meta,
                List.of(
                        new Pdf.Section(null, new Pdf.Table(List.of(), List.of(25, 75), parties, Set.of())),
                        new Pdf.Section(
                                "Lines (" + lines.size() + ")",
                                new Pdf.Table(
                                        List.of("Description", "Category", "Qty", "Unit price", "Received", "Total"),
                                        List.of(36, 14, 8, 14, 10, 18),
                                        body,
                                        Set.of(2, 3, 4, 5))),
                        new Pdf.Section(
                                null,
                                new Pdf.Table(
                                        List.of(),
                                        List.of(82, 18),
                                        List.of(List.of(
                                                "Total (" + order.getCurrency() + ")",
                                                MoneyText.of(total, order.getCurrency()))),
                                        Set.of(1)))),
                "ProcureFlow purchase order",
                Instant.now());
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug, actorId, "DOCUMENT_EXPORTED", "purchase_order", order.getId(), Map.of(), Map.of("format", "pdf")));
        return content;
    }

    @Transactional(readOnly = true)
    public List<OrderExportRow> exportRows(String tenantSlug, PurchaseOrder.Status status, UUID actorId) {
        long total = orders.page(tenantSlug, status, 0, 1).totalElements();
        if (total > Spreadsheet.MAX_EXPORT_ROWS) {
            throw ApiException.badRequest(
                    "EXPORT_TOO_LARGE",
                    "Export is limited to " + Spreadsheet.MAX_EXPORT_ROWS + " rows; narrow the filters",
                    Map.of("matching", String.valueOf(total)));
        }
        if (total == 0) {
            return List.of();
        }
        List<PurchaseOrder> found = orders.page(tenantSlug, status, 0, (int) total).content();
        Set<UUID> supplierIds =
                found.stream().map(PurchaseOrder::getSupplierId).collect(Collectors.toSet());
        Set<UUID> requestIds =
                found.stream().map(PurchaseOrder::getRequestId).collect(Collectors.toSet());
        Map<UUID, SupplierLookup.Card> vendors = suppliers.cardsInTenant(supplierIds, tenantSlug);
        Map<UUID, RequestLookup.Card> titles = requests.cardsInTenant(requestIds, tenantSlug);
        Map<UUID, Long> totals = orders.totalsFor(found.stream().map(PurchaseOrder::getId).toList());
        List<OrderExportRow> rows = new ArrayList<>();
        for (PurchaseOrder order : found) {
            SupplierLookup.Card vendor = vendors.get(order.getSupplierId());
            RequestLookup.Card request = titles.get(order.getRequestId());
            rows.add(new OrderExportRow(
                    order.getId(),
                    vendor == null ? order.getSupplierId().toString() : vendor.name(),
                    request == null ? order.getRequestId().toString() : request.title(),
                    order.getStatus().name(),
                    order.getCurrency(),
                    totals.getOrDefault(order.getId(), 0L),
                    order.getCreatedAt()));
        }
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "purchase_order",
                null,
                Map.of(),
                Map.of("format", "xlsx", "rows", rows.size())));
        return rows;
    }

    private static String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
