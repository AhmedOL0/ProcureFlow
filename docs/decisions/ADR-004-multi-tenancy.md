# ADR-004: Multi-tenant architecture

- Status: Accepted (Phase 1)
- Context: B2B SaaS serving many companies from one deployment; tenant
  data must never leak across tenants.
- Decision: Shared database, shared schema, `tenant_id` discriminator with
  defense in depth (JWT claim, request-scoped TenantContext, tenant-scoped
  queries, cross-tenant negative tests). See
  `docs/architecture/multi-tenancy.md`.
- Consequences: Simplest to operate and migrate. Rules out per-tenant
  schema customization; revisit only on a documented compliance demand.
