package com.procureflow.identity.application;

import java.util.Set;

/**
 * Permission codes. The V1-seeded {@code permissions} rows are the source of
 * truth for what exists; this class mirrors them for readable service code.
 * Add a code here only together with its seed row (V-migration) and the
 * endpoints that require it.
 */
public final class PermissionCodes {

    public static final String TENANT_ADMIN = "tenant:admin";
    public static final String TENANT_MANAGE = "tenant:manage";
    public static final String USER_MANAGE = "user:manage";
    public static final String DEPARTMENT_MANAGE = "department:manage";
    public static final String SUPPLIER_READ = "supplier:read";
    public static final String SUPPLIER_WRITE = "supplier:write";
    public static final String PROCUREMENT_REQUEST = "procurement:request";
    public static final String PROCUREMENT_APPROVE = "procurement:approve";
    public static final String BUDGET_READ = "budget:read";
    public static final String BUDGET_MANAGE = "budget:manage";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_WRITE = "order:write";
    public static final String INVOICE_READ = "invoice:read";
    public static final String INVOICE_WRITE = "invoice:write";
    public static final String ANALYTICS_READ = "analytics:read";
    public static final String AI_USE = "ai:use";
    public static final String AUDIT_READ = "audit:read";

    public static final Set<String> ALL = Set.of(
            TENANT_ADMIN, TENANT_MANAGE, USER_MANAGE, DEPARTMENT_MANAGE,
            SUPPLIER_READ, SUPPLIER_WRITE, PROCUREMENT_REQUEST, PROCUREMENT_APPROVE,
            BUDGET_READ, BUDGET_MANAGE, ORDER_READ, ORDER_WRITE,
            INVOICE_READ, INVOICE_WRITE, ANALYTICS_READ, AI_USE, AUDIT_READ);

    public static final Set<String> MEMBER_DEFAULTS = Set.of(
            PROCUREMENT_REQUEST, SUPPLIER_READ, ORDER_READ, INVOICE_READ, ANALYTICS_READ);

    private PermissionCodes() {
        // constants only
    }
}
