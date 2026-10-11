# ProcureFlow — Progress Tracker

Live state of the project. Update after every meaningful change.

## Current phase

**Phase 13 — enterprise hardening + directory pagination (in progress).**
C1 invite-gated join + email verification is merged; C2 server pagination
is implemented backend + frontend with all gates green (backend 140 IT,
frontend build + 29 Vitest + 48 Playwright). Remaining: C3 backup drill,
C4 audit-search/unread filters, final push + live probes.
Phases 1–11 are delivered (see Completed + the merged PRs #1–#11 and open
#12/#13): backend 113 IT + unit/ArchUnit green, frontend 24 Vitest + 18
Playwright green, live-verified screens in `docs/screens/`. This pass:
regression gates, security/a11y/perf review with evidence, infra + CI
review, README/portfolio overhaul, demo workspace docs.

## Completed

### Phase 13a — C1 invite-gated join + email verification (merged)
- V17 invites (single-use atomic redeem) + V18 email verification
  (24h TTL) + `users.verified` (grandfathered true); known-slug join
  without a live invite answers 403 `INVITE_REQUIRED`; forgot-password
  skips unverified addresses with the generic reply.
- `InviteIT` 6 + AccountIT verification tests; all IT `join()` helpers
  go through captured-mail invites. Frontend: register `inviteToken`,
  `/verify-email` route, settings banner + resend, admin invite console.
- Verified: backend 140 IT green, frontend build + e2e green.

### Phase 13b — C2 directory pagination (this change)
- Backend: `Paged<T>` envelopes (size 1..100) on requests (+`/mine`
  with `q`), suppliers, orders, invoices, budgets, departments, users,
  memberships; unbounded list methods removed; paging/search IT.
- Frontend: all directories + dialogs + dashboard + workspace on server
  paging; supplier KPIs via size-1 total reads; `api-types.gen.ts` regen.
- Hardening found by the migration: `enrichSuppliers` now isolates
  per-section failures (catchError + array guards, Vitest-locked) instead
  of failing the whole row.
- E2E doubles fixed for envelopes: `/suppliers?` disambiguation,
  `/categories` + `/invites` stubs, Users-region scoping.
- Verified: tsc 0, build 0, 29 Vitest, 48/48 Playwright (chromium+firefox).

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
- Phase 1 hardening attack (2026-10-11, commits 65cb7ff..798e434, all
  pushed): auth throttling (per-account 10/min + per-IP budgets, 429s,
  live-probed), scheduler tenant scoping (+ regression test, proven red),
  V14 auth_throttle ledger, V15 hot-path indexes + invoice-line FK,
  invoice/order race tests, suspend/reactivate + role-update endpoints
  (audited, self-change refused, refresh locked out) with AccountIT
  16→19, AI monthly token cap + provider retry/429 mapping (+7 tests).
  Full `./mvnw -B verify` green (123 IT + unit/arch, floors met);
  frontend build + 28 Vitest + 19 e2e green;   container rebuilt, throttle
  and profile live-probed. Two lessons: throttle records need
  REQUIRES_NEW (rollback erases them), and conditional beans escape the
  suite (Groq two-constructor crash caught only by container boot —
  fixed with @Autowired).
- Architecture stages B–G (2026-10-11, Stage A assessment committed
  first): throttle ledgers unified (V16 drops the reset table),
  `ArchitectureGatesTest` (foreign-infra ban + per-handler auth),
  `AuthorizationMatrixIT` (10 routes × member/admin/anonymous),
  budget GROUP BY rewrite (BudgetIT + ApprovalIT race green), axe gate
  on 5 pages (fixed a real critical chip-role violation), Firefox
  matrix, ADR-010 (outbox deferred with triggers). Full `verify` green
  (126 IT + unit/arch, floors met); frontend 48 e2e across both
  browsers. Commits 9069db8, 146b98f, d4bdb3d, fbe2483, all pushed.
- Deferred-item attack (2026-10-11): N+1 bulk-fetch rewrites (invoice
  booking/stats, order receive/stats, lane matching, categories,
  primary clearing — suites incl. races green), paged audit + inbox
  slice (shared Paged envelope, server MatPaginators, api-types
  regenerated, e2e doubles updated), takeUntilDestroyed on all 14
  form subscriptions, conditional-bean structural gate + AI-enabled
  boot slice (caught FakeAiProvider breaking AI-on wiring),
  backend-ci api-contract diff job. Full `verify` 129 IT green;
  frontend build + 28 Vitest + 48 e2e green; containers rebuilt,
  envelopes + throttle live-probed, notifications paginator
  screenshotted. One parallel-load login flake seen twice in
  full-matrix runs (never in isolation or CI-with-retries); a11y
  helper timeout widened to 15s.
- Enterprise readiness audit (read-only, 2026-10-10): 9-file package in
  `docs/enterprise-readiness/` (executive summary, feature matrix,
  security, architecture/data, UX, testing, DevOps, roadmap, release
  checklist). Verdict: strong honest MVP, not yet enterprise-complete;
  top gaps are login throttling, open slug join, no pagination, unwired
  ops/backup. Gates re-run green for the audit (115 IT + 16 unit/arch,
  28 Vitest, 19 e2e).
- Main has merged PR #12 (account) and PR #13 (hardening) on GitHub;
  this branch just integrated origin/main, so the account endpoints
  (GET/PATCH /users/me, change-password, forgot/reset) and the hardening
  pass are now in this line. Local `feature/account-management` and
  `feature/hardening-pass` branches are stale.
- Account management backend (merged to main as #12): profile edits,
  password changes, email-link reset via Brevo (16× AccountIT).
- Security hardening (merged to main as #13): HSTS + frame-deny,
  explicit inbox auth, audit redaction + unit test.
- Frontend line (`feature/frontend-operations`): design system, emerald
  re-theme, governance pages, workspace, copilot, account settings UI,
  Phase 10–11 work, plus the #12/#13 integration above.
- Design-system consolidation (committed 2ad157b on `feature/frontend-operations`,
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
  in this line) → matched `/users/{id}` → UUID conversion of `"me"` threw
  `MethodArgumentTypeMismatchException` → no handler → 500 + stack trace.
  Fixed with an explicit 400 (`INVALID_PATH_VARIABLE`) in
  `RestExceptionHandler` + `InvalidPathVariableIT` (red before: 500 on
  /users/me and /suppliers/not-a-uuid; green after). Obsolete as a
  Settings symptom now that #12 is integrated — kept as contract armor
  for every UUID route. Full `./mvnw -B verify` green (99 IT + 14
  unit/arch, coverage floors met); container rebuilt, live re-probe
  answered 400 with no stack in logs.

## Next up

1. Account Settings end to end: DONE 2026-10-10 — backend rebuilt from
   this branch, `GET /users/me` answers 200, Settings page loads with
   profile + password sections (screenshot in Temp); save/change flows
   covered by AccountIT 16/16 on the merged tree. Label nit fixed
   ("New password" overlapped the visibility toggle).
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
