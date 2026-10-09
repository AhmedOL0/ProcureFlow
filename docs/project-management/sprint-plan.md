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

## Phase 4 — Money governance
Epics 5-6 (+9). Approval workflows with delegation/escalation, budget
reservation in-transaction, event-driven notifications.

## Phase 5 — Payables
Epics 7-8 (+12). Full purchase orders, invoices, payment status, audit trail.

## Phase 6 — Intelligence
Epics 10-11 (+14 hardening). Analytics KPIs, Groq adapter, copilot,
request extraction, quotation intelligence, coverage floors, perf budgets.

Each phase ends with: all gates green, docs synced, demoable increment.
No phase starts until the previous gates pass.
