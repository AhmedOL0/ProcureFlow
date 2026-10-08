package com.procureflow.organization.api;

import com.procureflow.organization.domain.Department;
import java.util.UUID;

public record DepartmentResponse(UUID id, String name, UUID parentId) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getParent() == null ? null : department.getParent().getId());
    }
}
