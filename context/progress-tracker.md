# ProcureFlow — Progress Tracker

Live state of the project. Update after every meaningful change.

## Current phase

**Phase 12 — production release & portfolio readiness (this review).**
Phases 1–11 are delivered (see Completed + the merged PRs #1–#11 and open
#12/#13): backend 113 IT + unit/ArchUnit green, frontend 24 Vitest + 18
Playwright green, live-verified screens in `docs/screens/`. This pass:
regression gates, security/a11y/perf review with evidence, infra + CI
review, README/portfolio overhaul, demo workspace docs.

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
- RBAC: 17-permission catalog (V2 seed), per-tenant default roles
  (TENANT_ADMIN, APPROVER, FINANCE, OFFICER, AUDITOR, MEMBER),
  `@PreAuthorize` enforcement, tenant-before-permission. Provisioning is
  additive: older tenants gain new roles without losing grants.
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

### Phase 4 - Money governance (merged)
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

### Phase 5 - Payables (merged)
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

- Tests: 5× AnalyticsIT, 6× AiIT + 2 unit, 2× MetricsIT (+4 arch rules).
  Full gate 107/107 green (93 IT + 14 unit/arch) with floors holding.
- Typegen regenerated; frontend build + Vitest + Playwright green.

### Frontend auth (merged via #5)
- Angular Material v21 (decision recorded 2026-10-09) + animations.
- Login/register screens (reactive forms, ambiguous-tenant and conflict
  states), session dashboard (user/tenant/roles + sign-out).
- `AuthService` (signals, localStorage session, single-flight refresh),
  401 interceptor with one retry, guard with session restore.
- Tests: 4× Vitest (token helpers) + 4× Playwright (guard redirect,
  validation, backend-down error, register navigation). Build green.

### Phase 7 - Frontend foundation + suppliers slice (merged via #8)
- Design system from the stitch reference: navy/blue/teal/canvas tokens,
  Inter (self-hosted), status pills, dense tables, shared header/empty/
  error/confirm components, central Lucide catalog.
- Authenticated shell: navy rail (dashboard + suppliers only — no stubs),
  breadcrumbs, notification inbox entry (unread badge, mark-read), user menu,
  tenant footer; drawer overlays closed on handsets, tables scroll at 360px.
- Auth per contract: body-token refresh (no cookie transport exists —
  verified zero Set-Cookie in backend), JWT authorities drive `hasAuthority`
  hints, backend stays the enforcer.
- Suppliers vertical slice: server-filtered list (client sort/page — the
  contract returns full arrays), dossier with contacts (primary rule
  server-side), categories + inline catalog create, scorecards; write UI
  gated on `supplier:write`; 403/404/conflict states everywhere.
- Backend additions (required, minimal): env-gated CORS config (dev origin
  only) + OPTIONS permitAll for preflights; fixed dead dev fileReplacements.
- Tests: 17× Vitest, 6× Playwright in CI; live browser pass against real
  backend (login → list → dossier, mobile 360px, Stitch directory) with
  screenshots. Backend `verify` still 107/107 green after the CORS change.

### Phase 8 - Procurement workflows end to end (merged via #10)
- Requests: directory (server status filter + chips, client search/sort/page),
  dossier with items, lifecycle actions, approve/reject with comments,
  decision + lane + budget-context + history cards.
- Approval inbox: pending + recently decided with verdicts; no actions on
  decided rows.
- Orders: directory + fulfillment dossier (send/receive/close/cancel by
  status), create from approved requests, originating-request links.
- Invoices: directory + 3-way-match dossier, booking against sent orders,
  payments to PAID with overpay rejected.
- Admin: tenant rename, departments, memberships, users with roles at
  creation (no role-update endpoint exists — stated, not simulated).
- Audit: filterable read-only trail with expandable before/after payloads.
- Tests: 19× Vitest, 8× Playwright in CI; full procure-to-pay journey
  verified live in Chromium with screenshots (register → paid + admin/audit).
  Backend `verify` untouched and green (107/107).

## In progress

- Phase 12 review (this pass) on `feature/frontend-operations`.

## In progress

- Account management backend (`feature/account-management` → `main`,
  PR #12 OPEN, mergeable): profile edits, password changes, email-link
  reset via Brevo (16× AccountIT, 113 IT green). Merge BEFORE any
  settings-UI merge — the frontend depends on its endpoints.
- Security hardening (`feature/hardening-pass` → `main`, PR #13 OPEN):
  HSTS + frame-deny, explicit inbox auth, audit redaction + unit test.
- Frontend line (`feature/frontend-operations`, includes merged #10):
  unmerged commits → design system, emerald re-theme, governance pages,
  workspace, copilot, account settings UI, Phase 10–11 work. Next PR
  after #12 merges.
- Design-system consolidation (uncommitted on `feature/frontend-operations`,
  2026-10-10): every page migrated onto shared `pf-*` primitives in
  `styles.scss` (sr-only, loading, columns, card-head, panel, list, rows,
  row-link, muted, meter, head-row, stat-pill, form-row, notice, chips,
  count, table-scroll, dialog rhythm); 10 dead stylesheets deleted
  (admin-cards, workspace, order/invoice lists, budget detail, 5 dialog
  sheets); raw px/gap values mapped to tokens; approval inbox rebuilt on
  `pf-table-card`. Verified: `npm run build` + 28 Vitest + 19 Playwright
  green, screenshots dashboard/suppliers/users/390px in Temp.
- Path-var 400 fix (on `feature/frontend-operations`, 2026-10-10): visiting
  Account Settings fired `GET /api/v1/users/me` (PR #12 endpoint, not yet
  deployed) → matched `/users/{id}` → UUID conversion of `"me"` threw
  `MethodArgumentTypeMismatchException` → no handler → 500 + stack trace.
  Two layers: (1) expected pre-merge gap — settings UI needs #12 merged
  first, plan unchanged; (2) real contract bug — every UUID route 500'd on
  non-UUID values. Fixed (2) with an explicit 400 (`INVALID_PATH_VARIABLE`)
  in `RestExceptionHandler` + `InvalidPathVariableIT` (red before: 500 on
  /users/me and /suppliers/not-a-uuid; green after). Full
  `./mvnw -B verify` green (99 IT + 14 unit/arch, coverage floors met);
  container rebuilt, live re-probe answers 400 with no stack in logs.

## Next up

1. Merge #12 → #13 → frontend line (dependency order).
2. Brevo live proof needs confirmed login email + SMTP-key provenance +
   a validated sender (currently 535 on auth).
3. Beyond release: pagination envelopes, login/AI rate limits, inbox
   bulk decisions endpoint, SMTP for real delivery.

## Known limitations (carried, not hidden)

- Email delivery unverified live; reset flow proven via mocks + log-mode.
- No login/AI rate limiting (BCrypt-12 + no enumeration today).
- Approval inbox does per-row decision reads (N+1, fine at current scale).
- Frontend ~1.3MB total JS / 6KB main across lazy chunks (measured, no
  perf work taken).
- Dev-server discipline: kill by PORT (netstat→PID), never by cmdline
  substring; check `NG_EXIT`/`$LASTEXITCODE` immediately, never via a pipe.

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
- Coverage floors: enforced (`pom.xml`: line 0.70 / branch 0.40 fail the build).
