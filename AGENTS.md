# ProcureFlow — Agent Instructions

AI-powered multi-tenant B2B procurement & supplier management SaaS.
Modular Spring Boot monolith (Java 21) + Angular 21 SPA + PostgreSQL.

## Session entry point

Read these files in order before implementing or deciding anything
(they are the session entry point; this file is the deep reference and
must stay in sync with them):

1. `context/project-overview.md` — product definition, goals, phases, scope
2. `context/architecture.md` — system structure, boundaries, invariants
3. `context/ui-context.md` — frontend conventions + anti-slop design gates
4. `context/code-standards.md` — implementation rules (no-slop policy)
5. `context/ai-workflow-rules.md` — git workflow, scoping, the local CI gate
6. `context/progress-tracker.md` — current phase, completed work, decisions

Update `context/progress-tracker.md` after each meaningful change. If a
change affects architecture, scope or standards, update the relevant
context file (and this file) in the same change.

---

## Quick Commands

| Task | Command |
|------|---------|
| Backend run (dev) | `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Backend run, custom port | add `-Dspring-boot.run.jvmArguments=-Dserver.port=8081` (see gotcha 15) |
| Backend verify (full gate) | `cd backend && ./mvnw -B verify` (needs Docker for Testcontainers) |
| Frontend dev | `cd frontend && npm start` (http://localhost:4200) |
| Frontend build / unit / e2e | `cd frontend && npm run build && npm test && npm run test:e2e` |
| Generate API types | boot backend, then `cd frontend && npm run gen:api` |
| Playwright browsers | `cd frontend && npx playwright install chromium` |
| Compose infra | `docker compose up -d postgres` (dev deps) or `up -d --build` (full) |
| Compose validate | `docker compose config --quiet` (needs `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `JWT_SECRET` set) |

---

## Architecture

```
ProcureFlow/
├── backend/     Spring Boot 3.5 (Java 21), all routes under /api/v1
├── frontend/    Angular 21 SPA (Signals, lazy features), port 4200
├── infrastructure/  docker compose, monitoring, terraform skeleton
├── docs/        architecture, ADRs, api, testing, security, diagrams, PM
├── context/     six-file agent system (entry point, see above)
└── .github/workflows/  backend-ci, frontend-ci, security
```

### Key Design Decisions

- **Modular monolith, domain-oriented** — `com.procureflow.<module>/`
  `{domain,application,infrastructure,api}`; ArchUnit gates the rules in CI.
- **JWT + opaque refresh rotation** — 15-min HS512 access; single-use
  refresh (SHA-256 at rest); reuse revokes the whole chain (theft response).
- **Tenant check before permission check** — cross-tenant reads/writes
  answer 404, never 403 (no existence leakage).
- **Server-side totals and validation** — money as integer minor units +
  ISO currency; `Idempotency-Key` on creation, replays answer 200 with
  `X-Replay: true`.
- **Flyway append-only** — V1 core schema, V2 refresh+permissions,
  V3 suppliers, V4 purchase requests, V5–V13 decisions/workflows/budgets/
  notifications/orders/invoices/audit/AI/reset; `ddl-auto=validate`
  everywhere. A DB with a newer migration refuses older code — never
  restart a backend image older than the applied migration head.
- **AI behind a port** — `AiProvider` + live `GroqAiProvider`
  (`openai/gpt-oss-120b`, needs `GROQ_API_KEY` + `AI_ENABLED=true`,
  503 per use case while disabled); frontend never holds keys.

---

## Backend — Critical Patterns

### API surface (`docs/api/README.md` is the contract)
- Every controller under `/api/v1/...`. Consistent
  `ApiError{code,message,details?,traceId}` envelope via
  `RestExceptionHandler` — including malformed JSON (400), missing
  header/param (400), auth failures (401 JSON, not redirects).
- Validate with `@Valid` on records; null fields in update DTOs mean
  "leave unchanged" (documented per DTO).

### Security (`identity/infrastructure/SecurityConfig.java`)
- Stateless, CSRF off, `JwtAuthenticationFilter` before
  `UsernamePasswordAuthenticationFilter`, `TenantFilter` after
  `AuthorizationFilter` (populates/clears `TenantContext` in `finally`).
- Public: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui*/**`,
  `/actuator/health`. Everything else authenticated + `@PreAuthorize`.
- `/auth/me` is permitAll-matched, so a null principal means 401 —
  handled explicitly, never NPE.

### Entities and fetching (OSIV is OFF)
- UUID PKs generated in `@PrePersist`; `timestamptz`; LAZY everywhere.
- Controllers map detached entities: load what's mapped via
  `@EntityGraph` (`tenant`, `items`); proxy `getId()` without a fetch is
  safe, any other lazy access outside the tx is a 500.
- Bidirectional `PurchaseRequest.items` exists for reads only; writes go
  through the item repository explicitly (no cascade).

### Repositories
- Tenant scoping via `@Query` (`u.tenant.slug = :slug`), not derived
  names (except trivial `findAllByTenant_Id`-style traversals).
- **Never pass null parameters**: Hibernate infers no JDBC type for nulls
  (enum AND string) — branch to dedicated queries and normalize blanks
  to `""` before `LIKE` (see gotcha 9).

### Transactions and cross-module contact
- `REQUIRES_NEW` isolated insert for idempotency races: a violated insert
  poisons its transaction, so catch-and-reload must live outside it
  (see `RequestCreator` + gotcha 10).
- Money-moving code locks rows; each such path carries a concurrency test.
- Cross-module contact only through application ports exchanging ids/DTOs
  (`TenantProvisioning`, `UserLookup`, `SupplierLookup`); entity
  association across modules by id (`getReference`), never by loading
  foreign aggregates.

### Dependencies (pinned in `backend/pom.xml`)
- Spring Boot 3.5.16 · jjwt 0.12.6 · springdoc 2.8.17 · ArchUnit 1.5.1 ·
  JaCoCo 0.8.12 · Flyway 11.7.2 **+ `flyway-database-postgresql:11.7.2`**
  (Flyway 11+ splits databases out — without it Postgres is "unsupported").
- `postgresql:42.7.12` pinned past Boot's managed version for
  CVE-2026-54291 (drop the pin once Boot manages ≥42.7.12).
- No `spring-boot-starter-flyway` artifact exists — use `flyway-core`.
- `spring-boot-testcontainers` is required for `@ServiceConnection`;
  failsafe (not surefire) runs `*IT` classes.

### Maven wrapper
- Hand-rolled `mvnw`/`mvnw.cmd` invoke
  `org.apache.maven.wrapper.MavenWrapperMain` with
  `-Dmaven.multiModuleProjectDirectory` (the jar has no Main-Class).
- The exec bit doesn't survive Windows: set it with
  `git update-index --chmod=+x backend/mvnw` (must show `100755`).

### Tests
- Unit: `JwtServiceTest` (no Spring). ArchUnit: `ArchitectureRulesTest`.
- Integration: `*IT` extend `AbstractIntegrationTest`
  (`@SpringBootTest` random port, `test` profile, TestRestTemplate).
- **Each IT class declares its own static `@ServiceConnection`
  container** — an inherited shared one is stopped between classes,
  pointing later classes at a dead port (gotcha 7).
- Fixtures use random slugs; no inter-test ordering dependence.
- `application-test.yml` carries NO datasource URL (container provides
  it) and a fixed 64+ char test JWT secret.

---

## Frontend — Critical Patterns

- **Angular 21 pinned**: CLI 22 requires Node ≥24.15, this machine has
  24.14 — do not upgrade the CLI without upgrading Node first.
- Standalone components, Signals for state, RxJS for streams, Reactive
  Forms for input. Lazy feature boundaries from `app.routes.ts`.
- API base from `src/environments/*` only. Contract types live in
  `src/lib/api-types.gen.ts` — regenerate via `npm run gen:api` (needs
  the backend up; script targets `:8081`, boot accordingly) after any
  API change, and commit the result.
- Vitest covers pure TS (`shared/utils` pattern — no TestBed needed);
  Playwright covers the shell plus stubbed critical journeys
  (`e2e/journeys.spec.ts`, deterministic doubles, no live backend/AI in CI;
  live browser passes stay manual with screenshots).
- Angular Material 21 (decided); `ui-context.md` gates every visual change
  (no generic AI aesthetic, real states, contrast, axe-clean).

---

## Infrastructure & CI

- Compose: postgres:16, redis:7, rabbitmq:3-management (+ backend/frontend
  builds). Required vars fail fast (`${VAR:?}`); backend/frontend join
  the same network.
- **Port 8080 is taken by a local Apache `httpd.exe`** — boot manual
  backends on 8081 and generate types against it. Kill stale `java.exe`
  backends before rebooting (orphans shadow fresh boots with stale APIs).
- `spring-boot:run` does NOT recompile — run `compile` first or serve
  stale classes. Prefer `curl.exe` over `Invoke-WebRequest` for probing
  (proxy quirks).
- Workflows: `backend-ci` (`./mvnw -B verify` + JaCoCo artifact),
  `frontend-ci` (build + Vitest + Playwright on Chromium),
  `security` (Trivy fs HIGH/CRITICAL gate + `npm audit`).
  Trivy action tags need the `v` prefix (e.g. `v0.36.0`).
- Monitoring configs only (Prometheus scrape, Grafana provisioning +
  starter dashboard); Terraform is an honest skeleton (`validate` only).

## Git Workflow

- `main` is always green. Work on `feature/<epic>` (rebase onto `main`
  before starting).
- Land via PR on GitHub (or local `--no-ff` merge when driving solo):
  green gates + reviewed diff + synced docs + UI evidence.
- Conventional Commits; every commit buildable; never force-push shared
  branches; never push red (local gate in `ai-workflow-rules.md`).

---

## Gotcha Index (earned the hard way)

1. CLI 22 ≠ Node 24.14 → Angular 21 pinned.
2. Wrapper jar has no `Main-Class` → invoke `MavenWrapperMain` explicitly.
3. `spring-boot-starter-flyway` doesn't exist → `flyway-core`.
4. JaCoCo version isn't Boot-managed → pin it.
5. `@ServiceConnection` needs `spring-boot-testcontainers`.
6. Surefire ignores `*IT` → failsafe runs them.
7. One static container per IT class, never inherited-shared.
8. Test JWT secret must be ≥64 chars — count, don't eyeball.
9. Null query params (enum AND string) break Hibernate type inference.
10. Idempotency loser needs an isolated transaction to replay.
11. Map-then-touch after tx ends needs `@EntityGraph` (OSIV off).
12. Null `@AuthenticationPrincipal` on public-matched routes → explicit 401.
13. Malformed JSON / missing header / path-var type mismatch need explicit
    400 handlers — a non-UUID value on a `/{id}` route otherwise falls
    through to 500 (`INVALID_PATH_VARIABLE` covers it).
14. Migration column types must match entity expectations (`VARCHAR(3)`).
15. Local Apache owns :8080 → develop backends on :8081.
16. `spring-boot:run` serves stale classes — `compile` first.
17. Probe with `curl.exe`, not `Invoke-WebRequest`.
18. Gitignore slash-patterns are root-anchored (`**/` to unanchor); the
    CLI's `frontend/.gitignore` re-whitelists editor files — trimmed to
    `extensions.json` only.
19. Trivy action refs need the `v` prefix.
20. JWT secret ≥64 chars enforced at boot; test profile carries its own.
21. Dev DB password lives in the `pgdata` volume from first init:
    recreating the container is not enough — reset with
    `ALTER USER ... PASSWORD` over the trust socket (or `down -v`).
    Boot throwaway backends with `java -D... -jar` using EXACT
    placeholder names (`-DSPRING_DATASOURCE_PASSWORD`,
    `-Dapp.jwt.secret`); relaxed dotted forms don't resolve `${...}`.
22. Kill dev servers by PORT (`netstat -ano` → PID), never by cmdline
    substring (`bin\ng" serve` never matches `ng serve`) — zombies serve
    stale bundles for hours and poison every verification after them.
23. Check build exit codes immediately (`$out = ...; $LASTEXITCODE`),
    never through a pipe — truncated output and wrong `$?` hide red builds.
24. `(ngSubmit)` without `[formGroup]` never fires (it is not a native
    event) — the form falls back to native GET navigation.
25. JWT base64url needs `=` padding restored before `atob` — Chromium
    tolerates the omission, other engines throw and every authority check
    silently fails.

---

## File References

| Area | Key Files |
|------|-----------|
| Security chain | `backend/.../identity/infrastructure/SecurityConfig.java`, `JwtAuthenticationFilter.java` |
| Tenant scoping | `backend/.../organization/infrastructure/web/TenantFilter.java`, `shared/multitenancy/TenantContext.java` |
| Auth logic | `backend/.../identity/application/AuthService.java`, `JwtService.java`, `RoleProvisioningService.java` |
| Problem envelope | `backend/.../shared/web/ApiException.java`, `RestExceptionHandler.java` |
| Migrations | `backend/src/main/resources/db/migration/` (V1–V4) |
| AI port | `backend/.../ai/application/AiProvider.java` (+ records) |
| Arch tests | `backend/src/test/java/com/procureflow/ArchitectureRulesTest.java` |
| IT base | `backend/src/test/java/com/procureflow/AbstractIntegrationTest.java` |
| API contract | `docs/api/README.md`, live `/v3/api-docs`, `frontend/src/lib/api-types.gen.ts` |
| Frontend shell | `frontend/src/app/app.{ts,html,routes.ts}`, `src/environments/*` |
| Compose | `docker-compose.yml` (required-var guards) |
| CI | `.github/workflows/{backend-ci,frontend-ci,security}.yml` |
| Planning | `docs/project-management/{epics,initial-backlog,sprint-plan}.md` |
| Live state | `context/progress-tracker.md` |
