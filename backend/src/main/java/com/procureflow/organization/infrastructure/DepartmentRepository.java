package com.procureflow.organization.infrastructure;

import com.procureflow.organization.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for departments. Used only by the organization module. */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    @EntityGraph(attributePaths = {"parent"})
    @Query("select d from Department d where d.tenant.slug = :slug"
            + " and lower(d.name) like lower(concat('%', :query, '%'))")
    Page<Department> findPageByTenantSlug(
            @Param("slug") String slug, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"parent"})
    @Query("select d from Department d where d.id = :id and d.tenant.slug = :slug")
    Optional<Department> findByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);

    @Query("select count(d) > 0 from Department d where d.tenant.slug = :slug and d.name = :name")
    boolean existsByTenantSlugAndName(@Param("slug") String slug, @Param("name") String name);
}
