package com.procureflow.supplier.infrastructure;

import com.procureflow.supplier.domain.SupplierCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for supplier categories. Used only by the supplier module. */
public interface SupplierCategoryRepository extends JpaRepository<SupplierCategory, UUID> {

    @Query("select c from SupplierCategory c where c.tenant.slug = :slug order by c.name")
    List<SupplierCategory> findAllByTenantSlug(@Param("slug") String slug);

    @Query("select c from SupplierCategory c where c.id = :id and c.tenant.slug = :slug")
    Optional<SupplierCategory> findByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);

    @Query("select count(c) > 0 from SupplierCategory c where c.tenant.slug = :slug and c.name = :name")
    boolean existsByTenantSlugAndName(@Param("slug") String slug, @Param("name") String name);
}
