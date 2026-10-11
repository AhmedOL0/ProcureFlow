package com.procureflow.invoice.infrastructure;

import com.procureflow.invoice.domain.Invoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for invoices. Used only by the invoice module. */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Invoice> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // Half-open month range, same contract as the order-side twin above.
    @Query("select i from Invoice i where i.tenantId = :tenantId"
            + " and i.createdAt >= :from and i.createdAt < :to order by i.createdAt desc")
    List<Invoice> findAllByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    Page<Invoice> findAllByTenantIdAndNumberContainingIgnoreCaseOrderByCreatedAtDesc(
            UUID tenantId, String number, Pageable pageable);

    Page<Invoice> findAllByTenantIdAndStatusAndNumberContainingIgnoreCaseOrderByCreatedAtDesc(
            UUID tenantId, Invoice.Status status, String number, Pageable pageable);

    boolean existsByTenantIdAndNumber(UUID tenantId, String number);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.tenantId = :tenantId")
    Optional<Invoice> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
