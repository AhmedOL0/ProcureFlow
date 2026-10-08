package com.procureflow.organization.application;

import com.procureflow.identity.application.UserLookup;
import com.procureflow.identity.domain.User;
import com.procureflow.organization.domain.Department;
import com.procureflow.organization.domain.Membership;
import com.procureflow.organization.infrastructure.DepartmentRepository;
import com.procureflow.organization.infrastructure.MembershipRepository;
import com.procureflow.shared.web.ApiException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership management. Users are referenced by id only (resolved through
 * the identity port); the association itself is owned here.
 */
@Service
@Transactional
public class MembershipService {

    private final MembershipRepository memberships;
    private final DepartmentRepository departments;
    private final UserLookup userLookup;
    private final EntityManager entities;

    public MembershipService(
            MembershipRepository memberships,
            DepartmentRepository departments,
            UserLookup userLookup,
            EntityManager entities) {
        this.memberships = memberships;
        this.departments = departments;
        this.userLookup = userLookup;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<Membership> listByTenant(String tenantSlug) {
        return memberships.findAllByTenantSlug(tenantSlug);
    }

    public Membership add(String tenantSlug, UUID userId, UUID departmentId) {
        Department department = departments
                .findByIdAndTenantSlug(departmentId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "Department not found"));
        if (!userLookup.existsInTenant(userId, tenantSlug)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User not found in this workspace");
        }
        if (memberships.existsByUser_IdAndDepartment_Id(userId, departmentId)) {
            throw ApiException.conflict("ALREADY_MEMBER", "User is already a member of this department");
        }
        return memberships.save(new Membership(entities.getReference(User.class, userId), department));
    }

    public void remove(String tenantSlug, UUID membershipId) {
        Membership membership = memberships
                .findById(membershipId)
                .filter(m -> m.getDepartment().getTenant().getSlug().equals(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("MEMBERSHIP_NOT_FOUND", "Membership not found"));
        memberships.delete(membership);
    }
}
