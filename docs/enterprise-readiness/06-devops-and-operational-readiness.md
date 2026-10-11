# DevOps & operational readiness

## CI (all three workflows read fully)

- `backend-ci.yml`: push-main + PR paths `backend/**` → Temurin 21 + Maven cache → `./mvnw -B verify` (unit+ArchUnit+ITs+JaCoCo gate) → JaCoCo artifact upload (always). No secrets involved. Solid.
- `frontend-ci.yml`: Node 24 + npm cache → `npm ci` → build → Vitest → Chromium install → stubbed Playwright e2e with `CI=true`. Solid for what it covers.
- `security.yml`: weekly + PR/push → Trivy fs (HIGH/CRITICAL gate) + `npm audit --audit-level=high`. No secret values referenced.
- Gaps: no lint (no eslint/prettier-check/spotless anywhere), no Docker build validation, no Terraform validate job, no migration-lint beyond Flyway-in-ITs, no secret-scan on backend, Trivy covers repo fs only (not DB images), no Playwright browser cache, no coverage-diff comment (floors only).

## Docker & environments

- Compose is honest local dev: pinned images, healthchecks on postgres/redis/rabbitmq, `depends_on: healthy` for backend, `unless-stopped` everywhere, fail-fast required vars (`POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `JWT_SECRET`), `.env.example` documents everything.
- Gaps: no healthcheck on backend/frontend; frontend `depends_on backend` without condition; frontend image runs nginx as root (backend correctly uses `USER app`); backend image skips tests (`-DskipTests` — acceptable for local, must not become the deploy path); no resource limits, no `read_only`/`cap_drop`; frontend API base baked at build time via sed (redeploy per env); nginx has no TLS (document TLS-terminator assumption); `CORS_ALLOWED_ORIGINS` defaults to localhost.
- Redis/RabbitMQ containers run with zero application usage — configured dead weight. Either land a real use (cache hot reads, event transport) or remove them from the default profile so the topology matches reality.

## Observability: configs, not a system

- Actuator exposes `health/info/prometheus`; only `/actuator/health` is public. Domain counters exist for decisions, overspends, reservations, invoices, payments, AI calls (asserted in `MetricsIT`).
- Prometheus scrape config + Grafana datasource + 2-panel dashboard + 4 alert rules (`alerts.yml`: overspend spike, 5xx approvals, AI failing, AI usage jump) all exist — but no Prometheus/Grafana service in compose, no Alertmanager/receiver, never exercised. Status: partially verified scaffold.
- No correlation IDs (only per-500 `traceId`), no structured/JSON logging, no centralized logging story.
- Minimum viable ops (P1/M total): add Prometheus (+Alertmanager receiver) to compose, add request-id MDC, keep JSON logging optional until a collector exists.

## Reliability & recovery

- No backup job, no restore test, no RPO/RTO (see `02` H3 — P0). Volumes (`pgdata/redisdata/rabbitdata`) are durability theater without dumps.
- Pool config is one number (`maximum-pool-size: 10`); no timeouts, no leak detection. Redis AOF is on (good) with nothing to persist. RabbitMQ persistence configured with no traffic.
- No rate limiting anywhere except reset-throttle; no graceful-degradation tests beyond AI-disabled 503s.
- Recovery procedures: only the demo-reset snippet in `docs/demo.md`. No incident runbook, no deployment health-verification beyond compose startup, no rollback story (images are `:latest`, unversioned — tag releases before any shared deploy).

## What "production-ready" still needs (ordered)

1. Backup sidecar + restore drill + runbook (P0/S).
2. Login throttling + placeholder-secret refusal (P0/S) — counted in `02`, repeated here because ops owns the deploy env.
3. Versioned image tags + rollback note (P1/S).
4. Prometheus/Alertmanager in compose + request IDs (P1/M).
5. Backend/frontend healthchecks + `depends_on` conditions (P1/S).
6. Lint jobs + Docker-build job + tf-validate job in CI (P2/S).
