package com.procureflow.invoice.application;

import com.procureflow.invoice.domain.Invoice;
import com.procureflow.invoice.domain.InvoiceLine;
import com.procureflow.invoice.domain.InvoicePayment;
import com.procureflow.invoice.infrastructure.InvoiceLineRepository;
import com.procureflow.invoice.infrastructure.InvoicePaymentRepository;
import com.procureflow.invoice.infrastructure.InvoiceRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.purchaseorder.application.InvoiceOrderPort;
import com.procureflow.shared.web.ApiException;
import com.procureflow.audit.application.AuditTrailLogged;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier invoices with a 3-way-match-lite: cumulative invoiced quantity
 * per order line can never exceed the ordered quantity, and the order row
 * locks while lines book so concurrent invoices serialize. Payments
 * accumulate to PAID; overpaying is rejected.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final InvoicePaymentRepository payments;
    private final InvoiceOrderPort orders;
    private final TenantProvisioning tenants;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meters;

    public InvoiceService(
            InvoiceRepository invoices,
            InvoiceLineRepository lines,
            InvoicePaymentRepository payments,
            InvoiceOrderPort orders,
            TenantProvisioning tenants,
            ApplicationEventPublisher events,
            MeterRegistry meters) {
        this.invoices = invoices;
        this.lines = lines;
        this.payments = payments;
        this.orders = orders;
        this.tenants = tenants;
        this.events = events;
        this.meters = meters;
    }

    @Transactional
    public Invoice create(String tenantSlug, UUID actorId, UUID orderId, String number, List<LineInput> inputs) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (number == null || number.isBlank()) {
            throw ApiException.badRequest("NUMBER_REQUIRED", "Invoice number is required");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw ApiException.badRequest("EMPTY_INVOICE", "At least one invoice line is required");
        }
        InvoiceOrderPort.LockedOrder order = orders.lockForInvoicing(tenantSlug, orderId);
        if (!"SENT".equals(order.status())
                && !"PARTIALLY_RECEIVED".equals(order.status())
                && !"RECEIVED".equals(order.status())) {
            throw ApiException.conflict("ORDER_NOT_INVOICABLE", "Only sent orders can be invoiced");
        }
        if (invoices.existsByTenantIdAndNumber(tenantId, number.trim())) {
            throw ApiException.conflict("DUPLICATE_NUMBER", "This invoice number already exists");
        }
        Map<UUID, InvoiceOrderPort.Line> orderLines = order.lines().stream()
                .collect(Collectors.toMap(InvoiceOrderPort.Line::id, Function.identity()));
        // One grouped sum for all input lines, not one query per line: same
        // snapshot (same locked transaction), identical over-invoice math.
        Map<UUID, Long> invoiced = new HashMap<>();
        for (Object[] row : lines.sumQuantityByTenantAndOrderItems(
                tenantId, inputs.stream().map(LineInput::orderItemId).toList())) {
            invoiced.put((UUID) row[0], (Long) row[1]);
        }
        long total = 0;
        for (LineInput input : inputs) {
            InvoiceOrderPort.Line orderLine = orderLines.get(input.orderItemId());
            if (orderLine == null) {
                throw ApiException.notFound("ORDER_ITEM_NOT_FOUND", "Order line not found on this order");
            }
            if (input.quantity() < 1) {
                throw ApiException.badRequest("INVALID_QTY", "Invoiced quantity must be positive");
            }
            long already = invoiced.getOrDefault(input.orderItemId(), 0L);
            if (already + input.quantity() > orderLine.quantity()) {
                throw ApiException.conflict("OVER_INVOICED", "Cannot invoice more than ordered");
            }
            total += (long) input.quantity() * orderLine.unitPriceMinor();
        }
        try {
            Invoice invoice = new Invoice(tenantId, orderId, number.trim(), total, order.currency());
            invoices.saveAndFlush(invoice);
            for (LineInput input : inputs) {
                InvoiceOrderPort.Line orderLine = orderLines.get(input.orderItemId());
                lines.save(new InvoiceLine(
                        invoice, input.orderItemId(), input.quantity(), orderLine.unitPriceMinor(),
                        orderLine.currency()));
            }
            events.publishEvent(AuditTrailLogged.now(
                    tenantSlug, actorId, "INVOICE_CREATED", "invoice", invoice.getId(), null,
                    Map.of("number", invoice.getNumber(), "totalMinor", invoice.getTotalMinor(),
                            "status", "UNPAID")));
            meters.counter("procureflow.invoices").increment();
            return invoice;
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE_NUMBER", "This invoice number already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<Invoice> list(String tenantSlug, Invoice.Status status) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (status == null) {
            return invoices.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return invoices.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }

    @Transactional(readOnly = true)
    public Invoice get(String tenantSlug, UUID id) {
        return scoped(tenantSlug, id);
    }

    @Transactional(readOnly = true)
    public MatchView match(String tenantSlug, UUID id) {
        Invoice invoice = scoped(tenantSlug, id);
        InvoiceOrderPort.LockedOrder order = orders.orderView(tenantSlug, invoice.getOrderId());
        Map<UUID, Long> invoicedByLine = lines.findAllByInvoice_IdOrderByCreatedAt(id).stream()
                .collect(Collectors.toMap(
                        InvoiceLine::getOrderItemId, l -> (long) l.getQuantity(), Long::sum));
        List<MatchLine> matchLines = new ArrayList<>();
        for (InvoiceOrderPort.Line orderLine : order.lines()) {
            matchLines.add(new MatchLine(
                    orderLine.id(),
                    orderLine.quantity(),
                    orderLine.receivedQty(),
                    invoicedByLine.getOrDefault(orderLine.id(), 0L)));
        }
        long paid = payments.sumPaidByInvoiceId(id);
        return new MatchView(invoice, matchLines, paid);
    }

    @Transactional
    public Invoice pay(String tenantSlug, UUID actorId, UUID id, long amountMinor) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (amountMinor < 1) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Payment amount must be positive");
        }
        Invoice invoice = invoices
                .lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
        long paid = payments.sumPaidByInvoiceId(id);
        if (paid + amountMinor > invoice.getTotalMinor()) {
            throw ApiException.conflict("OVERPAID", "Payment exceeds the invoice total");
        }
        String before = invoice.getStatus().name();
        payments.save(new InvoicePayment(invoice, amountMinor));
        invoice.applyPayments(paid + amountMinor);
        invoices.save(invoice);
        meters.counter("procureflow.invoice.payments").increment();
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug, actorId, "INVOICE_PAID", "invoice", invoice.getId(),
                Map.of("status", before, "paidMinor", paid),
                Map.of("status", invoice.getStatus().name(), "paidMinor", paid + amountMinor)));
        return invoice;
    }

    private Invoice scoped(String tenantSlug, UUID id) {
        return invoices
                .findByIdAndTenantId(id, tenants.requireTenantId(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    public record LineInput(UUID orderItemId, int quantity) {
    }

    public record MatchLine(UUID orderItemId, int orderedQty, int receivedQty, long invoicedQty) {
    }

    public record MatchView(Invoice invoice, List<MatchLine> lines, long paidMinor) {
    }
}
