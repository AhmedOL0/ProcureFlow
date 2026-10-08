package com.procureflow.organization.infrastructure;

import com.procureflow.organization.domain.Membership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for memberships. Used only by the organization module. */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Query("select m from Membership m where m.department.tenant.slug = :slug")
    List<Membership> findAllByTenantSlug(@Param("slug") String slug);

    boolean existsByUser_IdAndDepartment_Id(UUID userId, UUID departmentId);

    long countByDepartment_Id(UUID departmentId);
}
