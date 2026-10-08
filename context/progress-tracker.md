# ProcureFlow — Progress Tracker

Live state of the project. Update after every meaningful change.

## Current phase

**Phase 4 done — Money governance** (branches `feature/approval-workflow`,
`feature/budget-management`, `feature/notifications`, merged into
`feature/approval-workflow` for one PR). **Next: Phase 5 — Payables**
(`feature/purchase-orders`, `feature/invoicing`, `feature/audit-compliance`).

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

### Phase 4 — Money governance (on `feature/approval-workflow`, awaiting PR)
- Approval slice 1: V5 (`approval_decisions` immutable + `approval_delegations`
  bounded/revocable); `RequestDecisionPort` locks the request `FOR UPDATE` and
  flips status in the same transaction — concurrent deciders serialize, one wins.
- Approval slice 2: V6 (lanes by amount range, ordered steps, per-request
  assignments with due dates); only the step approver or a delegate may decide;
  overdue steps escalate lazily on access + hourly scheduler backstop.
- Budget: V7 (pots per tenant month + holds); approving reserves the total in
  the same tx (pot rows locked id-ordered, overspend 409, currency checked);
  cancelling an approved request releases; no pot means no governance.
- Notifications: V8 (inbox rows); first real `DomainEvent` consumers —
  `PurchaseRequestSubmitted/Decided` publish from submit/decide, listener runs
  AFTER_COMMIT in a fresh tx (never rolls back business work); skeleton mail.
- Cancel now accepts APPROVED (releases the hold); request snapshot on the
  decision port carries requester + total + currency for reservations/notify.
- Tests: 16× ApprovalIT, 10× BudgetIT, 6× NotificationIT (matrices, races,
  expiry, cross-tenant throughout). Full gate 70/70 green (60 IT + 10 unit/arch).
- Typegen regenerated; frontend build + Vitest + Playwright green.

## In progress

- Phase 4 PR (`feature/approval-workflow` → `main`): assembled, gates green,
  awaiting user review + PR creation (no `gh` on this machine — open via the
  compare URL).

## Next up (Phase 5 — Payables)

1. `feature/purchase-orders`: orders from APPROVED requests only, item
   snapshot, mandatory supplier link, lifecycle; explicit supplier reference
   guard (409) replacing the constraint-violation fallback.
2. `feature/invoicing`: invoices on orders, 3-way-match-lite, UNPAID/PARTIAL/PAID.
3. `feature/audit-compliance`: append-only `audit_events`, never fail the
   observed tx, admin read endpoint.
4. `feature/frontend-auth`: still parallel-trackable (needs the
   Material-vs-bespoke decision first).

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
- Domain events: publishers use `ApplicationEventPublisher`; observers use
  `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` so observer
  failure never rolls back business work (notifications first, audit next).
- Budget governance is opt-in per period: a month with no pot holds nothing,
  so pre-budget approval tests keep passing unchanged.

## Open questions

- Component library: Angular Material vs bespoke (blocks `frontend-auth`
  visual direction; see `ui-context.md` review gate).
- Refresh transport for web: HttpOnly cookie vs body token (decide with
  first client; mobile needs the body variant).
- OAuth2/social login: out of scope until a customer asks.
- Cloud provider + state backend for Terraform (DevOps epic).
- Coverage floors: enforce from Phase 3 (currently report-only).
