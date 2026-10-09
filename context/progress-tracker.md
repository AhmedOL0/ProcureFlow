# ProcureFlow — Progress Tracker

Live state of the project. Update after every meaningful change.

## Current phase

**Phase 5 done — Payables** (branches `feature/purchase-orders`,
`feature/invoicing`, `feature/audit-compliance`, merged into
`feature/purchase-orders` for one PR). **Next: Phase 6 — Intelligence +
hardening** (`feature/analytics`, `feature/ai-copilot`,
`feature/quality-testing`, `feature/devops-observability`).

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

### Phase 5 — Payables (on `feature/purchase-orders`, awaiting PR)
- Orders: V9 (`purchase_orders` with unique request link + mandatory
  supplier, `purchase_order_items` snapshotted with received tracking).
  Creation from APPROVED only; send flips the request ORDERED in the same
  tx; receipt accumulates per line to RECEIVED; supplier delete consults the
  explicit order reference guard (409).
- Invoicing: V10 (invoices with per-tenant unique numbers, lines by
  order-line id, payments). Booking locks the order row; cumulative
  invoiced per line never exceeds ordered (409); payments walk
  UNPAID → PARTIAL → PAID with overpay rejected; GET carries the
  ordered/received/invoiced match.
- Audit: V11 (`audit_events`, append-only). `AuditTrailLogged` events from
  decide, order transitions, invoice create/pay, budget create/delete;
  persisted AFTER_COMMIT in a fresh tx wrapped in catch-and-log — audit
  failure never fails business work. Reads are `audit:read` admin-only.
- Cancel now also accepts APPROVED requests (releases the budget hold).
- Tests: 8× OrderIT, 7× InvoiceIT, 5× AuditIT (approved-only, snapshot
  fidelity, races, 3-way match, payment walk, admin-only reads,
  cross-tenant throughout). Full gate 90/90 green (80 IT + 10 unit/arch).
- Typegen regenerated; frontend build + Vitest + Playwright green.

### Frontend auth (on `feature/frontend-auth`, awaiting PR)
- Angular Material v21 (decision recorded 2026-10-09) + animations.
- Login/register screens (reactive forms, ambiguous-tenant and conflict
  states), session dashboard (user/tenant/roles + sign-out).
- `AuthService` (signals, localStorage session, single-flight refresh),
  401 interceptor with one retry, guard with session restore.
- Tests: 4× Vitest (token helpers) + 4× Playwright (guard redirect,
  validation, backend-down error, register navigation). Build green.

## In progress

- Frontend-auth PR (`feature/frontend-auth` → `main`): implemented
  (Material v21 per the recorded decision), gates green, awaiting PR creation.

## Next up (remaining)

1. `feature/frontend-auth` PR, then merge open PRs in dependency order.
2. Data screens next (suppliers/requests/approvals list UIs) if you want a
   fully clickable procure-to-pay in the browser.

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

- Component library: **decided 2026-10-09 — Angular Material v21** (tables,
  a11y, speed for the data-dense back office; themed to pass the anti-slop
  gates). Unblocks `frontend-auth` and all feature screens.
- Refresh transport for web: HttpOnly cookie vs body token (decide with
  first client; mobile needs the body variant).
- OAuth2/social login: out of scope until a customer asks.
- Cloud provider + state backend for Terraform (DevOps epic).
- Coverage floors: enforce from Phase 3 (currently report-only).
