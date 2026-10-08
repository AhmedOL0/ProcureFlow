# Testing strategy

## Pyramid

- **Unit** (JUnit 5 + Mockito, Vitest on frontend): pure business rules,
  validators, pricing math, mappers. Fast, no Spring, no browser.
- **Integration** (Testcontainers + PostgreSQL): repositories against the
  real migration chain; every tenant-scoped query gets a cross-tenant
  negative case; money-moving code gets a concurrency test
  (two threads, one row, `FOR UPDATE`).
- **Architecture** (ArchUnit, runs on every build): module boundary rules
  from `docs/architecture/modular-monolith.md`.
- **API** (Spring `@SpringBootTest` + MockMvc/RestAssured-style checks):
  status codes, envelopes, auth rejection paths, idempotency replays.
- **Frontend component** (Vitest + Testing Library, Phase 3+): behavior of
  smart components with mocked services.
- **E2E** (Playwright): critical journeys only (login, request-to-approval
  happy path, smoke of every route). No exhaustive UI permutations.

## Gates

- `backend-ci`: `mvn verify` (unit + integration + ArchUnit + JaCoCo report).
  Coverage is reported, not gatekept, until Phase 3; then per-module floors.
- `frontend-ci`: `npm run build`, `npm test`, `playwright test`.
- Flaky tests are quarantined the same day they flake; a red main branch
  blocks all merges until green.

## Phase 1 state

ArchUnit rules + one Vitest spec + one Playwright smoke spec exist and run
green. Testcontainers wiring (`application-test.yml`) is ready; the first
real integration test lands with the first repository in Phase 2.
