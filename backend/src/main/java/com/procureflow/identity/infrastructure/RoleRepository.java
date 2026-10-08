package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for roles. Used only by the identity module. */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findAllByTenant_Id(UUID tenantId);

    @Query("select r from Role r where r.tenant.slug = :slug and r.name = :name")
    Optional<Role> findByTenantSlugAndName(@Param("slug") String slug, @Param("name") String name);

    @Query("select r from Role r where r.tenant.slug = :slug")
    List<Role> findAllByTenantSlug(@Param("slug") String slug);
}
