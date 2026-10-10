# ProcureFlow — Architecture

Deep reference: `docs/architecture/` (overview, modular-monolith,
multi-tenancy, security) and `docs/decisions/ADR-001..009`.

## System structure

```
ProcureFlow/
├── backend/     Spring Boot 3.5 (Java 21), modular monolith, /api/v1
├── frontend/    Angular 21 SPA (Signals, lazy features), port 4200
├── infrastructure/  docker compose, monitoring, terraform skeleton
├── docs/        architecture, ADRs, api, testing, security, diagrams, PM
├── .github/workflows/  backend-ci, frontend-ci, security
└── context/     this Six-File system
```

## Backend modules and boundaries

Twelve modules under `com.procureflow.<module>/{domain,application,
infrastructure,api}`: identity, organization, supplier, procurement,
approval, budget, purchaseorder, invoice, notification, audit, analytics,
ai — plus `shared/{kernel,multitenancy,web}`.

Dependency policy (ArchUnit-enforced in CI):
1. `api` → `application`/`domain` only. Never `infrastructure`.
2. `application` → own `infrastructure` repos + cross-module ports only.
3. `domain` → `shared.kernel` only. No Spring imports.
4. Cross-module contact via application ports exchanging ids/DTOs; entity
   association across modules by id (`getReference`), never by loading
   foreign aggregates. `Membership → User` is the reference example.
5. `shared` never depends on a business module. `analytics` is read-only.
   `notification`/`audit` consume domain events; nothing calls them synchronously.

## Storage and messaging

- PostgreSQL 16 is the only source of truth. UUID PKs, `timestamptz`,
  Flyway `V<n>` migrations (append-only), `ddl-auto=validate` everywhere.
  Flyway 11+ needs the separate `flyway-database-postgresql` artifact.
- Redis: cache and rate limiting only, never authoritative.
- RabbitMQ: async transport for domain events (in-process Spring events
  until listeners land; publishers never change when transport does).
- Money-moving code locks rows (`SELECT ... FOR UPDATE`) inside one
  transaction and carries a concurrency test. No distributed transactions.

## Security invariants

- JWT access (15 min, HS512, ≥64-char secret refused at boot) + opaque
  single-use refresh tokens (SHA-256 at rest, reuse revokes the chain).
- RBAC permission codes as authorities; `@PreAuthorize` on endpoints;
  tenant check before permission check; cross-tenant reads answer 404,
  never 403.
- `TenantContext` is set post-authorization and always cleared in
  `finally` (leaked tenants across pooled threads = data incident).
- BCrypt-12 passwords. Secrets via env only (`.env` git-ignored).

## AI invariants

- Angular never calls Groq and never holds keys. Flow: Angular → Spring
  Boot → `ai` module (prompts, validation, auth, usage metering) → Groq.
- `GroqAiProvider` is live (`openai/gpt-oss-120b`); the module stays safe
  without credentials (`AI_ENABLED=false` → 503 per use case).

## Observability

Actuator exposes `health/info/prometheus`; Prometheus scrape + Grafana
provisioning live in `infrastructure/monitoring/`. Domain counters
(requests, approvals) and AI usage metrics arrive with their features.
