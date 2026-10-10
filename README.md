# ProcureFlow

**AI-powered multi-tenant B2B procurement and supplier management SaaS.**

ProcureFlow helps buying organizations run the full procure-to-pay flow in one
place: suppliers, purchase requests, approval workflows, budgets, purchase
orders and invoices, with an AI copilot that explains spend and drafts
requests. One backend serves many tenants; tenant data never crosses tenant
boundaries.

> Status: **Phase 8 — procurement workflows end to end.** The Angular
> frontend now runs the full procure-to-pay story in the browser: requests
> with lifecycle and approvals, orders with receipt, invoices with payment
> tracking, workspace admin and a read-only audit trail — all against the
> real API, no simulated data. See `docs/project-management/sprint-plan.md`
> for the delivery record.

## Business problem

Mid-size companies buy through email threads and spreadsheets: approvals are
untraceable, budgets are checked after the money is spent, and supplier
performance lives in people's heads. ProcureFlow makes every step explicit,
auditable and fast: request → budget reservation → approval → order → invoice,
with AI assistance where judgment is needed.

## Target users

- **Requesters** — employees raising purchase requests and tracking them.
- **Approvers / managers** — approve or reject with delegation and escalation.
- **Procurement officers** — manage suppliers, quotations and purchase orders.
- **Finance** — budgets, invoices, payment status, spend analytics.
- **Tenant admins** — users, departments, roles, approval workflows.

## Architecture

Modular Spring Boot monolith (Java 21) + Angular SPA, PostgreSQL, Redis,
RabbitMQ. Business domains own their code vertically
(`domain / application / infrastructure / api`); ArchUnit tests enforce the
boundaries in CI.

- `docs/architecture/overview.md` — system context and principles
- `docs/architecture/modular-monolith.md` — module rules and dependency policy
- `docs/architecture/multi-tenancy.md` — tenant isolation strategy
- `docs/architecture/security.md` — authN/authZ design
- `docs/decisions/` — ADRs (start with ADR-001)

## Technology stack

| Layer | Choice |
|---|---|
| Frontend | Angular 21, TypeScript, Signals, RxJS, Reactive Forms, Vitest, Playwright |
| Backend | Java 21, Spring Boot 3.5, Security, Data JPA, Flyway, ArchUnit |
| Data | PostgreSQL 16 (Flyway migrations, no `ddl-auto=create` in prod), Redis 7, RabbitMQ 3 |
| AI | Groq behind an `AiProvider` port (backend only; needs `GROQ_API_KEY` + `AI_ENABLED=true`) |
| Infra | Docker Compose, GitHub Actions, Prometheus + Grafana, Terraform skeleton |

## Local development

Prerequisites: JDK 21, Node 24, Docker.

```bash
cp .env.example .env   # then fill in POSTGRES_PASSWORD, JWT_SECRET, ...
docker compose up -d postgres redis rabbitmq
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm start   # http://localhost:4200
```

API docs (backend running): `http://localhost:8080/swagger-ui.html`

The dev profile allows the local SPA origin (`http://localhost:4200`) for
CORS; production allows nothing unless `CORS_ALLOWED_ORIGINS` is set.

## API surface

All routes live under `/api/v1/...` with a consistent problem envelope
(`docs/api/README.md` is the contract; live OpenAPI at `/v3/api-docs`,
Swagger UI at `/swagger-ui.html`). Angular types are generated from the
live contract (`cd frontend && npm run gen:api`, checked in at
`src/lib/api-types.gen.ts`).

| Group | Endpoints | Highlights |
|---|---|---|
| Auth | `/auth`, `/users` | JWT + rotating refresh, RBAC, tenant provisioning |
| Organization | `/tenants/current`, `/departments`, `/memberships` | Tenant-scoped workspaces and roles |
| Suppliers | `/suppliers`, `/supplier-contacts`, `/supplier-categories` | CRUD, single-primary contacts, scorecards |
| Procurement | `/purchase-requests`, `/purchase-request-items` | Idempotent create (`Idempotency-Key`), draft lifecycle |
| Approvals | `/approvals/...` | Immutable decisions, delegations, amount lanes, escalation |
| Budgets | `/budgets` | Monthly pots, in-transaction reservation, 409 overspend |
| Orders | `/purchase-orders` | From approved requests only, snapshot lines, receipt |
| Invoices | `/invoices` | 3-way match, UNPAID → PARTIAL → PAID |
| Notifications | `/notifications` | Event-driven personal inbox |
| Audit | `/audit-events` | Append-only trail, admin read |
| Analytics | `/analytics/...` | Spend, supplier and approval KPIs (read-only) |
| AI copilot | `/ai/...` | KPI-cited chat, spend explanations, request extraction, quotation comparison (metered, `ai:use`) |
| Frontend auth | `/login`, `/register`, `/dashboard` | Angular Material session shell (guard + refresh interceptor) |
| Frontend workflows | `/dashboard`, `/workspace`, `/suppliers`, `/requests`, `/requests/mine`, `/approvals`, `/orders`, `/invoices`, `/budgets`, `/analytics`, `/copilot`, `/notifications`, `/admin`, `/admin/users`, `/admin/departments`, `/admin/workflows`, `/admin/audit` | Directory + dossier screens with lifecycle actions, all API-backed |

## Testing strategy

Full picture in `docs/testing/strategy.md`. Local gates (must pass before
every push):

- Backend: `cd backend && ./mvnw verify` — 107 tests green (14 unit/ArchUnit
  + 93 Testcontainers integration over real PostgreSQL 16): auth, tenant
  isolation, suppliers, procurement, approvals (incl. decision races),
  budgets (incl. reservation race), orders, invoices, notifications, audit,
  analytics, copilot, metrics. JaCoCo floors (line 0.70 / branch 0.40) fail
  the build on regression.
- Frontend unit: `cd frontend && npm test` (Vitest, 24 tests)
- Frontend E2E: `cd frontend && npx playwright install chromium && npm run test:e2e`
  (8 tests: guarded shell incl. budgets/analytics/notifications/admin splits, auth states, route redirects)
- API types: boot the backend, then `cd frontend && npm run gen:api` after
  any API change, and commit the result.

## AI architecture

Angular never calls Groq. Flow: Angular → Spring Boot → `ai` module
(prompts, validation, auth, usage metering) → Groq API. The `GroqAiProvider`
is live behind `AI_ENABLED` (default model `openai/gpt-oss-120b`, 503 per
use case while disabled); prompts are
versioned (`Prompts.VERSION`, metered per call), outputs are validated, and
prompts carry KPI aggregates only — no user identities or secrets. Use
cases: KPI-cited chat, spend explanations, request extraction, quotation
comparison, plus a per-tenant usage endpoint.

## Security

JWT access + rotating refresh tokens, RBAC with fine-grained permissions,
tenant isolation enforced server-side, BCrypt password hashing, secure
headers. Secrets come from env vars only (see `docs/security/secrets.md`).
Details: `docs/architecture/security.md`.

## Observability

Actuator exposes `health`, `info` and `prometheus`; Prometheus + Grafana
configs live in `infrastructure/monitoring/` (first alert rules in
`prometheus/alerts.yml`: overspend spikes, 5xx rate, AI failures/usage).
Domain counters (`procureflow.decisions`, `procureflow.budget.*`,
`procureflow.invoices*`, `procureflow.ai.calls`) ship from the services;
AI token usage is metered per tenant in `ai_usage`.

## CI/CD

`.github/workflows/`: `backend-ci.yml` (build, unit, ArchUnit), `frontend-ci.yml`
(build, Vitest, Playwright), `security.yml` (Trivy fs scan, npm audit).
Docker images build from `backend/Dockerfile` and `frontend/Dockerfile`.
Deployment (Terraform target) is designed, not yet implemented.

## Project management

Jira-ready planning docs (no Jira connection yet): `docs/project-management/`
holds `epics.md` (14 epics), `initial-backlog.md` (user stories with
acceptance criteria) and `sprint-plan.md` (phased delivery plan).

## License

Proprietary portfolio project. All rights reserved.
