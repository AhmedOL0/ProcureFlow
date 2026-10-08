# ProcureFlow

**AI-powered multi-tenant B2B procurement and supplier management SaaS.**

ProcureFlow helps buying organizations run the full procure-to-pay flow in one
place: suppliers, purchase requests, approval workflows, budgets, purchase
orders and invoices, with an AI copilot that explains spend and drafts
requests. One backend serves many tenants; tenant data never crosses tenant
boundaries.

> Status: **Phase 2 — identity & organization done.** JWT authentication with
> refresh rotation, RBAC, tenant-scoped tenants/departments/memberships and
> integration tests are implemented and green. Business features
> (procurement and beyond) land incrementally per
> `docs/project-management/sprint-plan.md`.

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
| AI | Groq behind an `AiProvider` port (backend only; disabled until Phase 6) |
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

## Testing strategy

Full picture in `docs/testing/strategy.md`. Phase 1 gates:

- Backend: `cd backend && ./mvnw verify` (unit + Testcontainers integration
  + ArchUnit + JaCoCo report)
- Frontend unit: `cd frontend && npm test` (Vitest)
- Frontend E2E: `cd frontend && npx playwright install chromium && npm run test:e2e`
- Integration tests (Testcontainers + PostgreSQL) cover auth flows and
  tenant isolation since Phase 2; each new repository adds its own.

## AI architecture

Angular never calls Groq. Flow: Angular → Spring Boot → `ai` module
(prompts, validation, auth, usage metering) → Groq API. The provider
abstraction (`AiProvider`) exists now; the Groq adapter, prompt library and
the four designed use cases (copilot, analytics explanations, request
extraction, quotation intelligence) land in Phase 6 behind `AI_ENABLED=true`.

## Security

JWT access + rotating refresh tokens, RBAC with fine-grained permissions,
tenant isolation enforced server-side, BCrypt password hashing, secure
headers. Secrets come from env vars only (see `docs/security/secrets.md`).
Details: `docs/architecture/security.md`.

## Observability

Actuator exposes `health`, `info` and `prometheus`; Prometheus + Grafana
configs live in `infrastructure/monitoring/`. Planned metrics cover HTTP,
JVM, DB, cache, domain events (requests, approvals) and AI usage
(count, latency, errors, tokens).

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
