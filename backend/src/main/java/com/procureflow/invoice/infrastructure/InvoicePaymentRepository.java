package com.procureflow.invoice.infrastructure;

import com.procureflow.invoice.domain.InvoicePayment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for invoice payments. Used only by the invoice module. */
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {

    List<InvoicePayment> findAllByInvoice_IdOrderByCreatedAt(UUID invoiceId);

    @Query("select coalesce(sum(p.amountMinor), 0) from InvoicePayment p where p.invoice.id = :invoiceId")
    long sumPaidByInvoiceId(@Param("invoiceId") UUID invoiceId);

    /**
     * One grouped sum per invoice (rows are [invoiceId, paid]). Callers must
     * skip empty collections — {@code IN ()} is invalid JPQL.
     */
    @Query("select p.invoice.id, coalesce(sum(p.amountMinor), 0) from InvoicePayment p"
            + " where p.invoice.id in :invoiceIds group by p.invoice.id")
    List<Object[]> sumPaidByInvoiceIds(@Param("invoiceIds") Collection<UUID> invoiceIds);
}
