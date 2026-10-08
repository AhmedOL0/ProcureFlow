# ProcureFlow — Progress Tracker

Live state of the project. Update after every meaningful change.

## Current phase

**Phase 3 done (suppliers + procurement + typegen, merged to main).
Next: Phase 4 — Money governance** (branches: `feature/approval-workflow`,
`feature/budget-management`, `feature/notifications`).

## Completed

### Phase 1 — Foundation (commit 2e2a26c)
- Repo skeleton, 12 module boundaries + layer contracts, ArchUnit gates.
- V1 schema (tenants/users/roles/permissions/departments/memberships).
- `AiProvider` port, `TenantContext`, `DomainEvent`; compose stack; 3 CI
  workflows; README; 7 ADRs; epics/backlog/sprint plan.
- Verified: backend `verify` green (2 ArchUnit), frontend build + 4 Vitest
  + 1 Playwright smoke green, compose config valid.

### Phase 2 — Identity & Organization (commits c596f51, 41ee1db)
- JWT (HS512, 15 min, ≥64-char secret enforced at boot) + opaque
  single-use refresh rotation with theft detection (reuse revokes chain).
- Registration that provisions-or-joins a workspace; login with
  multi-tenant disambiguation (`409 LOGIN_AMBIGUOUS`); logout; `/me`.
- RBAC: 17-permission catalog (V2 seed), per-tenant TENANT_ADMIN/MEMBER
  roles, `@PreAuthorize` enforcement, tenant-before-permission.
- Tenants get/rename, department CRUD (delete refused when non-empty),
  membership add/remove — all tenant-scoped, cross-tenant reads/writes 404.
- `TenantFilter` populates/clears `TenantContext` post-authorization.
- Tests: 8× JwtServiceTest, 9× AuthFlowIT, 4× TenantIsolationIT —
  `mvn verify` 23/23 green (Testcontainers PG16, Flyway V1+V2 proven real).
- Remote `origin` set (github.com/AhmedOL0/ProcureFlow.git); `main` +
  13 `feature/*` branches pushed.

### Context system (this folder)
- Six files created; anti-slop code + design rules are merge gates.

### Phase 3 — Procurement core (merged to main)
- Suppliers: tenant-scoped CRUD with search, contacts (single-primary
  invariant), categories (assign/replace, in-use protection), monthly
  scorecards. OFFICER role added for operational writes.
- Purchase requests: draft lifecycle, idempotent create via
  `Idempotency-Key` (replay answers 200 + `X-Replay`), submit validation,
  cancel, item CRUD on drafts, supplier links validated per tenant.
- OpenAPI typegen proven end to end: live backend → `api-types.gen.ts`
  (26 endpoints), frontend build green with it.
- Tests: 7× SupplierIT, 7× ProcurementIT (incl. a real two-thread race on
  one idempotency key → exactly one row). Full suite 38/38 green.
- Remote: `feature/supplier-management` + `feature/procurement` pushed and
  merged with `--no-ff`; `main` green.

## In progress

- Nothing. Awaiting user go-ahead for Phase 3.

## Next up (Phase 3)

1. `feature/supplier-management`: suppliers, contacts, categories (+ repo
   tests with cross-tenant negatives).
2. `feature/procurement`: purchase requests + items, lifecycle,
   idempotent submit with concurrency test.
3. OpenAPI frontend type generation lands with the first real endpoints.
4. `feature/frontend-auth`: login UI, interceptor, guard (can parallel Phase 3).

## Architecture decisions (supplementing docs/decisions)

- Hibernate infers no JDBC type for null parameters: optional enum/string
  filters must branch to dedicated queries (never `IS NULL OR` with null
  params), and blanks normalize to `""` before `LIKE`.
- Idempotency races: the loser's transaction is poisoned, so creation is
  orchestrated outside a transaction (pre-check → isolated REQUIRES_NEW
  insert → reload-as-replay).

- Angular 21 (not 22): CLI 22 requires Node ≥24.15, machine has 24.14.
- Spring Boot 3.5.16 (mature line) + Flyway 11.7.2 (needs separate
  `flyway-database-postgresql` artifact).
- Minimal hand-rolled `mvnw` scripts invoking `MavenWrapperMain` work and
  are committed (wrapper jar has no Main-Class manifest).
- Testcontainers: each IT class owns its static container (inherited
  shared containers die between classes).
- `@ServiceConnection` over `jdbc:tc` URLs; failsafe runs `*IT`.
- Cross-module entity association by id (`getReference`), ports exchange
  primitives (rule recorded in `modular-monolith.md`).

## Open questions

- Component library: Angular Material vs bespoke (blocks `frontend-auth`
  visual direction; see `ui-context.md` review gate).
- Refresh transport for web: HttpOnly cookie vs body token (decide with
  first client; mobile needs the body variant).
- OAuth2/social login: out of scope until a customer asks.
- Cloud provider + state backend for Terraform (DevOps epic).
- Coverage floors: enforce from Phase 3 (currently report-only).
