# Multi-tenancy strategy

See ADR-004 for the decision record. This document states the mechanics.

## Model: shared database, shared schema, tenant discriminator

One PostgreSQL cluster, one schema, every tenant-scoped table carries
`tenant_id`. Chosen over schema-per-tenant (migration and connection-pool
pain at our scale) and database-per-tenant (operational cost without a
compliance driver). Revisit only for a documented regulatory requirement.

## Enforcement layers (defense in depth)

1. **Identity.** Users belong to exactly one tenant (`users.tenant_id`);
   the JWT carries `tenant_id` + `user_id` after login.
2. **Request scope.** A servlet filter (Phase 2) validates the JWT, sets
   `TenantContext` for the thread, and clears it after the request.
3. **Data access.** Repositories scope every query by the current tenant
   (derived query predicates or a Hibernate filter on `tenant_id`).
   A query without a tenant predicate is a bug; code review checks this.
4. **Tests.** Every repository/integration test includes a cross-tenant
   negative case: Tenant A fixtures must be invisible to Tenant B.

## What exists in Phase 1

- `tenants` table + `Tenant` entity (isolation root).
- `tenant_id` on `users`, `roles`, `departments`.
- `TenantContext` holder (populated by the Phase 2 filter).
- V1 migration establishes the columns; row-level enforcement and the
  negative tests arrive with the first tenant-scoped queries.

## Non-goals

Per-tenant encryption keys, tenant-specific schemas, and tenant-aware
migrations are explicitly out of scope until a customer contract demands
them (documented here if that day comes).
