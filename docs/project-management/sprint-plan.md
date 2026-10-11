# Sprint plan (phased delivery)

## Phase 1 — Foundation (done)
Repo, module boundaries, V1 schema, AI port, compose stack, CI skeletons,
ADRs, backlog. Gates: backend `verify` green, frontend build + Vitest +
Playwright smoke green.

## Phase 2 — Identity & Organization (done)
Epics 1-2. JWT + refresh rotation, RBAC, tenant filter + `TenantContext`
wiring, registration, departments, memberships. First Testcontainers
integration tests + cross-tenant negatives. Gates green
(`mvn verify`: 10 unit/arch + 13 integration).

## Phase 3 — Procurement core (done)
Epics 3-4 (+7 lite). Suppliers (CRUD, contacts with single-primary rule,
categories, monthly scorecards), purchase requests with idempotent submit
(unique key + race-to-replay, concurrency-tested), draft lifecycle, OpenAPI
frontend type generation (`npm run gen:api` → `api-types.gen.ts`). OFFICER
role added for operational writes. Gates green (`mvn verify`: 38/38).

## Phase 4 — Money governance (done)
Epics 5-6 (+9). Approval workflows with delegation/escalation, budget
reservation in-transaction, event-driven notifications. Gates green.

## Phase 5 — Payables (done)
Epics 7-8 (+12). Full purchase orders, invoices, payment status, audit trail.
Gates green.

## Phase 6 — Intelligence (done)
Epics 10-11 (+14 hardening). Analytics KPIs, Groq adapter, copilot,
request extraction, quotation intelligence, coverage floors, perf budgets.
Gates green (113 IT + unit/arch, JaCoCo floors holding).

## Phase 7 — Frontend foundation + suppliers (done, #8)
Angular shell, obsidian-emerald design system, supplier directory + dossier.

## Phase 8 — Procurement workflows (done, #10)
Requests, approval inbox, orders, invoices, admin and audit screens.

## Phase 9 — Governance + roles (done, #11)
Budgets, analytics, notifications, admin split, persona roles
(APPROVER/FINANCE/AUDITOR), My Requests, My Workspace.

## Phase 10 — Analytics & AI experience (done)
Dashboard financial pulse, copilot hardening (cancel, provenance),
account settings + email-link password reset (PR #12).

## Phase 11 — Product-wide hardening (this review)
Journeys E2E (stubbed doubles), security headers, audit redaction, a11y
pass, perf baselines, docs-vs-code sync.

## Phase 12 — Production release & portfolio (this review)
Infra/CI review, README/portfolio overhaul, demo workspace + reset docs,
clean verification. No cloud deployment without explicit authorization.

Each phase ends with: all gates green, docs synced, demoable increment.
No phase starts until the previous gates pass.
