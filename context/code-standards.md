# ProcureFlow — Code Standards

## General (no slop)

- Small, single-purpose units. Maintainability over cleverness; explicit
  business rules over clever abstractions. Strong typing everywhere.
- Fix root causes, never layer workarounds. No commented-out code, no
  `TODO` without a tracked follow-up, no dead code committed.
- No fake implementations presented as production-ready. Placeholders are
  namespaced, documented with their target phase, and fail fast with an
  explicit message (see `AiProvider`: interface only, zero beans).
- No giant classes/files: services stay focused; a file growing past
  ~250 lines is a split candidate. No `any`, no untyped maps as domain
  models, no stringly-typed business states (use enums/records).
- Keep API contracts explicit and `docs/` synchronized with the code in
  the same change.

## Backend (Java 21 + Spring Boot)

- Module layout `domain/application/infrastructure/api`; dependency
  policy in `architecture.md` (ArchUnit gates it — do not weaken the rules
  to make code compile; restructure the code instead).
- UUID PKs generated in `@PrePersist`; `timestamptz`; LAZY associations;
  `@EntityGraph` where controllers read across associations (OSIV stays off).
- Controllers are thin: validate (`@Valid`), call one use case, map to a
  record DTO. Business rules never live in controllers.
- Consistent `ApiError{code,message,details?,traceId}` envelope via
  `RestExceptionHandler`; no stack traces or secrets in responses.
- Auth: permissions as authorities, `@PreAuthorize` per endpoint,
  tenant-before-permission, cross-tenant 404. Money paths lock rows and
  carry concurrency tests. Idempotency keys on mutating client requests.
- Config from env with documented defaults; secrets never in code, tests,
  or logs. Flyway migrations append-only and proven against real Postgres
  (Testcontainers) before merge.
- No `this.method()` self-invocation for `@Async`/cache proxies; no
  field injection; constructor injection only.

## Frontend (Angular + TypeScript)

- Strict TS; Signals for component state; RxJS for async streams;
  Reactive Forms with validators for every form. Services return
  typed `Observable<T>`; components subscribe via `async` pipe.
- One smart container per route at most; everything else presentational
  (`input()`/`output()`). No business logic in templates.
- No hardcoded API URLs, no secrets, no direct Groq calls — ever.
- Unit-test pure logic with Vitest (no TestBed needed for utils);
  TestBed only for components with real behavior; E2E covers journeys,
  not permutations.
- Follow `ui-context.md` anti-slop rules; they are merge gates, not advice.

## Testing

- Pyramid per `docs/testing/strategy.md`: unit → Testcontainers
  integration (cross-tenant negative + concurrency where money moves) →
  ArchUnit → API → Vitest → Playwright smoke.
- Each test class owns its static `@ServiceConnection` container (an
  inherited shared container gets stopped between classes — documented
  pitfall, do not reintroduce).
- Unique fixtures per test (random slugs); no inter-test ordering
  dependence. Flaky tests are quarantined the same day.

## File organization

- `backend/src/main/java/com/procureflow/<module>/{domain,application,
  infrastructure,api}` + `shared/{kernel,multitenancy,web}`;
  `backend/src/main/resources/{application*.yml,db/migration}`.
- `frontend/src/{app/{core,shared,features,layout},environments,assets}`;
  `frontend/e2e/` for Playwright.
- `context/` — this Six-File system (live state in `progress-tracker.md`).
