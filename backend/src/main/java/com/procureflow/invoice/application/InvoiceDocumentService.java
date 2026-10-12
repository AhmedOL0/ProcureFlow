package com.procureflow.invoice.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.invoice.domain.Invoice;
import com.procureflow.invoice.domain.InvoiceLine;
import com.procureflow.invoice.infrastructure.InvoiceLineRepository;
import com.procureflow.invoice.infrastructure.InvoicePaymentRepository;
import com.procureflow.purchaseorder.application.InvoiceOrderPort;
import com.procureflow.shared.document.MoneyText;
import com.procureflow.shared.document.Pdf;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.ApiException;
import com.procureflow.shared.web.Paged;
import com.procureflow.supplier.application.SupplierLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Printable invoices and xlsx invoice exports. Descriptions resolve through
 * the order port (bulk), suppliers through the supplier port — entities
 * never cross modules, and foreign references degrade to bare ids.
 */
@Service
public class InvoiceDocumentService {

    public record InvoiceExportRow(
            String number,
            String order,
            String supplier,
            String status,
            String currency,
            long totalMinor,
            long paidMinor,
            Instant createdAt) {
    }

    private final InvoiceService invoices;
    private final InvoiceLineRepository lines;
    private final InvoicePaymentRepository payments;
    private final InvoiceOrderPort orders;
    private final SupplierLookup suppliers;
    private final ApplicationEventPublisher events;

    public InvoiceDocumentService(
            InvoiceService invoices,
            InvoiceLineRepository lines,
            InvoicePaymentRepository payments,
            InvoiceOrderPort orders,
            SupplierLookup suppliers,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.lines = lines;
        this.payments = payments;
        this.orders = orders;
        this.suppliers = suppliers;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public byte[] document(String tenantSlug, UUID actorId, UUID invoiceId) {
        Invoice invoice = invoices.get(tenantSlug, invoiceId);
        List<InvoiceLine> booked = lines.findAllByInvoice_IdOrderByCreatedAt(invoiceId);
        long paid = payments.sumPaidByInvoiceId(invoiceId);
        InvoiceOrderPort.LockedOrder order = orders.orderView(tenantSlug, invoice.getOrderId());
        SupplierLookup.Card vendor =
                suppliers.cardInTenant(order.supplierId(), tenantSlug).orElse(null);
        Map<UUID, String> descriptions = order.lines().stream()
                .collect(Collectors.toMap(
                        InvoiceOrderPort.Line::id, InvoiceOrderPort.Line::description, (first, second) -> first));

        List<String> meta = new ArrayList<>();
        meta.add("Workspace: " + tenantSlug + "  |  Status: " + invoice.getStatus().name());
        meta.add("Created: " + Pdf.stamp(invoice.getCreatedAt()));
        meta.add("Order: " + shortId(invoice.getOrderId()) + " (" + order.status() + ")");

        List<List<String>> parties = new ArrayList<>();
        parties.add(List.of("Supplier", vendor == null ? "-" : vendor.name()));
        if (vendor != null) {
            parties.add(List.of("Email", orDash(vendor.email())));
            parties.add(List.of("Address", orDash(vendor.address())));
        }
        parties.add(List.of("Currency", invoice.getCurrency()));

        List<List<String>> body = new ArrayList<>();
        for (InvoiceLine line : booked) {
            body.add(List.of(
                    descriptions.getOrDefault(line.getOrderItemId(), "-"),
                    String.valueOf(line.getQuantity()),
                    MoneyText.of(line.getUnitPriceMinor(), line.getCurrency()),
                    MoneyText.of(line.lineTotalMinor(), line.getCurrency())));
        }

        List<List<String>> totals = new ArrayList<>();
        totals.add(List.of("Total", MoneyText.of(invoice.getTotalMinor(), invoice.getCurrency())));
        totals.add(List.of("Paid", MoneyText.of(paid, invoice.getCurrency())));
        totals.add(List.of(
                "Balance due", MoneyText.of(Math.max(0, invoice.getTotalMinor() - paid), invoice.getCurrency())));

        byte[] content = Pdf.document(
                "Invoice " + invoice.getNumber(),
                meta,
                List.of(
                        new Pdf.Section(null, new Pdf.Table(List.of(), List.of(25, 75), parties, Set.of())),
                        new Pdf.Section(
                                "Lines (" + booked.size() + ")",
                                new Pdf.Table(
                                        List.of("Description", "Qty", "Unit price", "Total"),
                                        List.of(52, 10, 19, 19),
                                        body,
                                        Set.of(1, 2, 3))),
                        new Pdf.Section(
                                null, new Pdf.Table(List.of(), List.of(81, 19), totals, Set.of(1)))),
                "ProcureFlow invoice",
                Instant.now());
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug, actorId, "DOCUMENT_EXPORTED", "invoice", invoice.getId(), Map.of(), Map.of("format", "pdf")));
        return content;
    }

    @Transactional(readOnly = true)
    public List<InvoiceExportRow> exportRows(
            String tenantSlug, Invoice.Status status, String query, UUID actorId) {
        Paged<Invoice> first = invoices.page(tenantSlug, status, query, 0, 1);
        if (first.totalElements() > Spreadsheet.MAX_EXPORT_ROWS) {
            throw ApiException.badRequest(
                    "EXPORT_TOO_LARGE",
                    "Export is limited to " + Spreadsheet.MAX_EXPORT_ROWS + " rows; narrow the filters",
                    Map.of("matching", String.valueOf(first.totalElements())));
        }
        if (first.totalElements() == 0) {
            return List.of();
        }
        List<Invoice> found =
                invoices.page(tenantSlug, status, query, 0, (int) first.totalElements()).content();
        Set<UUID> orderIds = found.stream().map(Invoice::getOrderId).collect(Collectors.toSet());
        Map<UUID, InvoiceOrderPort.LockedOrder> views = orders.orderViews(tenantSlug, orderIds);
        Set<UUID> supplierIds = views.values().stream()
                .map(InvoiceOrderPort.LockedOrder::supplierId)
                .collect(Collectors.toSet());
        Map<UUID, SupplierLookup.Card> vendors = suppliers.cardsInTenant(supplierIds, tenantSlug);
        Map<UUID, Long> paid = paidBy(found.stream().map(Invoice::getId).toList());
        List<InvoiceExportRow> rows = new ArrayList<>();
        for (Invoice invoice : found) {
            InvoiceOrderPort.LockedOrder view = views.get(invoice.getOrderId());
            SupplierLookup.Card vendor = view == null ? null : vendors.get(view.supplierId());
            rows.add(new InvoiceExportRow(
                    invoice.getNumber(),
                    shortId(invoice.getOrderId()),
                    vendor == null ? "" : vendor.name(),
                    invoice.getStatus().name(),
                    invoice.getCurrency(),
                    invoice.getTotalMinor(),
                    paid.getOrDefault(invoice.getId(), 0L),
                    invoice.getCreatedAt()));
        }
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "DOCUMENT_EXPORTED",
                "invoice",
                null,
                Map.of(),
                Map.of("format", "xlsx", "rows", rows.size())));
        return rows;
    }

    private Map<UUID, Long> paidBy(List<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> paid = new HashMap<>();
        for (Object[] row : payments.sumPaidByInvoiceIds(invoiceIds)) {
            paid.put((UUID) row[0], (Long) row[1]);
        }
        return paid;
    }

    private static String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
