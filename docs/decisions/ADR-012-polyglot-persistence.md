# ADR-012: Polyglot persistence assessment (no build)

- Status: Assessment recorded 2026-10-14. No new database added.
- Context: Phase 0 review of the whole repo (modules, schema, AI
  workflows, search, tests, compose). PG-only since the Redis/AMQP
  removal (zero code refs proven); all search is tenant-scoped LIKE
  substring; no JSONB/full-text/vectors anywhere; k6 p95 20.5 ms shows
  no hot path; copilot retrieves nothing (prompts carry aggregates).

## Verdicts

| Store | Verdict | Why | Revisit trigger |
|---|---|---|---|
| MongoDB | Approved design, **unbuilt** (owner deferred) | Versioned `extract-request` results are the one genuine document workload; everything else stays in PG | Owner asks for the slice |
| Redis | Rejected (documented) | Throttles DB-backed, sessions stateless, no measured hot path; re-adding contradicts the lean topology | Measured hot path needing cache |
| OpenSearch | Rejected | LIKE-substring over small tenant tables suffices | Cross-entity full-text (PG tsvector first) |
| pgvector/Qdrant | Rejected | No document corpus to retrieve over; a vector DB today is inflation | Real corpus (policies, catalogs); pgvector-first |
| Cassandra/Neo4j | Rejected | No workload | Evidence + concrete use case |

## Approved-but-unbuilt slice (MongoDB extraction history)

Collection `ai_extractions`: `{tenantId, schemaVersion: 1,
modelProvider/Identifier, promptVersion, input, validatedDraft,
validation: {ok, failures}, createdRequestId?, createdAt}`, TTL 90 d,
compound index `(tenantId, createdAt desc)`. Persist best-effort after
`AiValidator` passes (Mongo down → warn + meter
`procureflow.ai.history_dropped` + still answer; derived-store posture
per ADR-010). `GET /api/v1/ai/extractions` paged + `GET …/{id}`,
both tenant-scoped with a negative cross-tenant IT. Draft prices stay
proposals until the normal guarded create path re-validates them.

## Standing rules for any future store

PG stays authoritative for tenants, money, approvals and audit;
tenant-from-security-context everywhere with negative tests;
derived stores rebuildable with documented retention; no distributed
transactions; Testcontainers ITs, never mocks-only; no accuracy claims
without measured evaluation.
