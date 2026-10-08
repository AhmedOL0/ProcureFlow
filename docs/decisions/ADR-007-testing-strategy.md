# ADR-007: Automated testing strategy

- Status: Accepted (Phase 1)
- Context: Procurement moves money; regressions are expensive and tenant
  leaks are security incidents.
- Decision: Pyramid with teeth — unit (rules), Testcontainers integration
  (repositories + concurrency + cross-tenant negatives), ArchUnit
  (boundaries), API tests, Vitest + Playwright on the frontend.
  See `docs/testing/strategy.md`.
- Consequences: Slower suites than pure unit tests, but every layer that
  can break trust is covered. Coverage reported from day one, floors enforced
  from Phase 3.
