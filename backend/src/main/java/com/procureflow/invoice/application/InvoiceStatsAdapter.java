package com.procureflow.invoice.application;

import com.procureflow.invoice.domain.Invoice;
import com.procureflow.invoice.infrastructure.InvoicePaymentRepository;
import com.procureflow.invoice.infrastructure.InvoiceRepository;
import com.procureflow.organization.application.TenantProvisioning;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link InvoiceStatsPort} over the invoice module's own repositories. */
@Service
public class InvoiceStatsAdapter implements InvoiceStatsPort {

    private final InvoiceRepository invoices;
    private final InvoicePaymentRepository payments;
    private final TenantProvisioning tenants;

    public InvoiceStatsAdapter(
            InvoiceRepository invoices, InvoicePaymentRepository payments, TenantProvisioning tenants) {
        this.invoices = invoices;
        this.payments = payments;
        this.tenants = tenants;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceStat> invoiceStats(String tenantSlug) {
        List<Invoice> found = invoices.findAllByTenantIdOrderByCreatedAtDesc(tenants.requireTenantId(tenantSlug));
        if (found.isEmpty()) {
            return List.of();
        }
        // One grouped sum for all invoices, not one query per row.
        Map<UUID, Long> paid = new HashMap<>();
        for (Object[] row : payments.sumPaidByInvoiceIds(found.stream().map(Invoice::getId).toList())) {
            paid.put((UUID) row[0], (Long) row[1]);
        }
        return found.stream()
                .map(invoice -> new InvoiceStat(
                        invoice.getId(),
                        invoice.getStatus().name(),
                        invoice.getTotalMinor(),
                        invoice.getCurrency(),
                        invoice.getCreatedAt(),
                        paid.getOrDefault(invoice.getId(), 0L)))
                .toList();
    }
}
