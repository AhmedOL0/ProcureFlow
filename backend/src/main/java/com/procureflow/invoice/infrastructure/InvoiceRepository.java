package com.procureflow.invoice.infrastructure;

import com.procureflow.invoice.domain.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for invoices. Used only by the invoice module. */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Invoice> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Invoice> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, Invoice.Status status);

    boolean existsByTenantIdAndNumber(UUID tenantId, String number);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.tenantId = :tenantId")
    Optional<Invoice> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
