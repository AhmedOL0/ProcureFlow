package com.procureflow.organization.application;

import com.procureflow.organization.domain.Department;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.organization.infrastructure.DepartmentRepository;
import com.procureflow.organization.infrastructure.MembershipRepository;
import com.procureflow.organization.infrastructure.TenantRepository;
import com.procureflow.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department CRUD, always scoped to the caller's tenant. Deletes are refused
 * while memberships reference the department (explicit 409 instead of a raw
 * constraint violation).
 */
@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departments;
    private final MembershipRepository memberships;
    private final TenantRepository tenants;

    public DepartmentService(
            DepartmentRepository departments, MembershipRepository memberships, TenantRepository tenants) {
        this.departments = departments;
        this.memberships = memberships;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public List<Department> list(String tenantSlug) {
        return departments.findAllByTenantSlug(tenantSlug);
    }

    public Department create(String tenantSlug, String name, UUID parentId) {
        if (departments.existsByTenantSlugAndName(tenantSlug, name)) {
            throw ApiException.conflict("DEPARTMENT_EXISTS", "A department with this name already exists");
        }
        Tenant tenant = tenants
                .findBySlug(tenantSlug)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "Workspace not found"));
        Department department = new Department(tenant, name);
        if (parentId != null) {
            Department parent = departments
                    .findByIdAndTenantSlug(parentId, tenantSlug)
                    .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "Parent department not found"));
            department.setParent(parent);
        }
        return departments.save(department);
    }

    public Department rename(String tenantSlug, UUID id, String name) {
        Department department = scoped(tenantSlug, id);
        if (!department.getName().equals(name) && departments.existsByTenantSlugAndName(tenantSlug, name)) {
            throw ApiException.conflict("DEPARTMENT_EXISTS", "A department with this name already exists");
        }
        department.setName(name);
        return departments.save(department);
    }

    public void delete(String tenantSlug, UUID id) {
        Department department = scoped(tenantSlug, id);
        if (memberships.countByDepartment_Id(id) > 0) {
            throw ApiException.conflict("DEPARTMENT_NOT_EMPTY", "Department still has members; move them first");
        }
        departments.delete(department);
    }

    @Transactional(readOnly = true)
    public Department scoped(String tenantSlug, UUID id) {
        return departments
                .findByIdAndTenantSlug(id, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "Department not found"));
    }
}
