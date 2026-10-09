package com.procureflow.invoice.application;

import com.procureflow.invoice.infrastructure.InvoicePaymentRepository;
import com.procureflow.invoice.infrastructure.InvoiceRepository;
import com.procureflow.organization.application.TenantProvisioning;
import java.util.List;
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
        return invoices.findAllByTenantIdOrderByCreatedAtDesc(tenants.requireTenantId(tenantSlug)).stream()
                .map(invoice -> new InvoiceStat(
                        invoice.getId(),
                        invoice.getStatus().name(),
                        invoice.getTotalMinor(),
                        invoice.getCurrency(),
                        invoice.getCreatedAt(),
                        payments.sumPaidByInvoiceId(invoice.getId())))
                .toList();
    }
}
