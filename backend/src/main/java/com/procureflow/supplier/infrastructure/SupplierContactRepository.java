package com.procureflow.supplier.infrastructure;

import com.procureflow.supplier.domain.SupplierContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for supplier contacts. Used only by the supplier module. */
public interface SupplierContactRepository extends JpaRepository<SupplierContact, UUID> {

    @Query("select c from SupplierContact c where c.supplier.id = :supplierId"
            + " and c.supplier.tenant.slug = :slug order by c.primary desc, c.name")
    List<SupplierContact> findAllBySupplier(@Param("supplierId") UUID supplierId, @Param("slug") String slug);

    @Query("select c from SupplierContact c where c.id = :id and c.supplier.tenant.slug = :slug")
    Optional<SupplierContact> findByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);

    List<SupplierContact> findAllBySupplier_Id(UUID supplierId);
}
