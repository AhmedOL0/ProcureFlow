package com.procureflow.invoice.infrastructure;

import com.procureflow.invoice.domain.InvoiceLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for invoice lines. Used only by the invoice module. */
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    List<InvoiceLine> findAllByInvoice_IdOrderByCreatedAt(UUID invoiceId);

    @Query("select coalesce(sum(l.quantity), 0) from InvoiceLine l"
            + " join l.invoice i where i.tenantId = :tenantId and l.orderItemId = :orderItemId")
    long sumQuantityByTenantAndOrderItem(
            @Param("tenantId") UUID tenantId, @Param("orderItemId") UUID orderItemId);

    /**
     * One grouped sum per order line (rows are [orderItemId, invoiced]).
     * Callers must skip empty collections — {@code IN ()} is invalid JPQL.
     */
    @Query("select l.orderItemId, coalesce(sum(l.quantity), 0) from InvoiceLine l"
            + " join l.invoice i where i.tenantId = :tenantId and l.orderItemId in :orderItemIds"
            + " group by l.orderItemId")
    List<Object[]> sumQuantityByTenantAndOrderItems(
            @Param("tenantId") UUID tenantId, @Param("orderItemIds") Collection<UUID> orderItemIds);
}
