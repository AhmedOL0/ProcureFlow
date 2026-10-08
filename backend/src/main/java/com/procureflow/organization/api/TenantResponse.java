package com.procureflow.organization.api;

import com.procureflow.organization.domain.Tenant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String slug, String status) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus().name());
    }
}
