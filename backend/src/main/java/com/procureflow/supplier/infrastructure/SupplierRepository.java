package com.procureflow.supplier.infrastructure;

import com.procureflow.supplier.domain.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for suppliers. Used only by the supplier module. */
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    // NOTE: no nullable parameters anywhere in these queries. Hibernate
    // cannot infer a JDBC type for a null parameter (this bit us for both
    // the enum status and the optional search string), so the service
    // normalizes blanks to "" and branches on status instead.
    @EntityGraph(attributePaths = {"tenant"})
    @Query("select s from Supplier s where s.tenant.slug = :slug"
            + " and lower(s.name) like lower(concat('%', :query, '%'))"
            + " order by s.name")
    Page<Supplier> findPageByTenantSlug(
            @Param("slug") String slug, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant"})
    @Query("select s from Supplier s where s.tenant.slug = :slug and s.status = :status"
            + " and lower(s.name) like lower(concat('%', :query, '%'))"
            + " order by s.name")
    Page<Supplier> findPageByTenantSlugAndStatus(
            @Param("slug") String slug,
            @Param("status") Supplier.Status status,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = {"tenant"})
    @Query("select s from Supplier s where s.id = :id and s.tenant.slug = :slug")
    Optional<Supplier> findByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);

    @Query("select count(s) > 0 from Supplier s where s.tenant.slug = :slug and s.name = :name")
    boolean existsByTenantSlugAndName(@Param("slug") String slug, @Param("name") String name);

    long countByCategories_Id(UUID categoryId);
}
