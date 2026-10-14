# ADR-012: Polyglot persistence — MongoDB for AI extraction history (built)

- Status: Built 2026-10-14 (`feature/ai-extraction-history`). No other
  store added; all rejections below stand with their triggers.
- Context: Phase 0 review (2026-10-14) found one genuine document
  workload: `extract-request` returns validated drafts and forgets them.
  PG-only since the Redis/AMQP removal (zero code refs proven); all
  search is tenant-scoped LIKE; no JSONB/full-text/vectors; copilot
  retrieves nothing (prompts carry aggregates).

## Verdicts

| Store | Verdict | Why | Revisit trigger |
|---|---|---|---|
| MongoDB | **Built** (this ADR) | Versioned extraction results + provenance; PG untouched | — |
| Redis | Rejected (documented) | Throttles DB-backed, sessions stateless, no measured hot path | Measured hot path needing cache |
| OpenSearch | Rejected | LIKE-substring over small tenant tables suffices | Cross-entity full-text (PG tsvector first) |
| pgvector/Qdrant | Rejected | No document corpus to retrieve over | Real corpus; pgvector-first |
| Cassandra/Neo4j | Rejected | No workload | Evidence + concrete use case |

## Built slice

Collection `ai_extractions`: `{tenantSlug, schemaVersion: 1,
modelIdentifier, promptVersion, input, title, priority,
items[] (description/quantity/unitPriceMinor), createdRequestId, createdAt}`,
TTL 90 d, compound index `(tenantSlug, createdAt desc)`. Persisted best-effort after
`AiValidator` passes (Mongo down → warn-once + meter
`procureflow.ai.history_dropped` + draft still answered); reads fail
loud (503 `HISTORY_UNAVAILABLE`). `GET /api/v1/ai/extractions` paged +
`GET …/{id}`, both tenant-scoped with indistinguishable 404s.
`POST …/{id}/request` (+ `Idempotency-Key`, `ai:use` +
`procurement:request`) creates a real draft through the `RequestDrafting`
port — same guarded, idempotent path as POST /purchase-requests with
X-Replay mirrored — then links `createdRequestId` (best-effort, heals on
replay). The copilot "Recent drafts" shelf lists history with per-row
create-or-opened states.
`modelProvider` was dropped from the draft spec: the model identifier
already carries provenance without touching the provider interface.
Draft prices stay proposals until the guarded create path re-validates.

## Standing rules for any future store

PG stays authoritative for tenants, money, approvals and audit;
tenant-from-security-context everywhere with negative tests;
derived stores rebuildable with documented retention; no distributed
transactions; Testcontainers ITs, never mocks-only; no accuracy claims
without measured evaluation.

## Verification (this pass)

`ExtractionHistoryIT` 4/4 (persist+shape, cross-tenant 404s, ai:use
403, compound + TTL index presence incl. expireAfterSeconds=7776000),
`ExtractionDegradedIT` 1/1 (200 + meter + 503), `CopilotServiceTest`
save-captor, ArchUnit gates green, full backend `verify` + frontend
gates green, api-types regenerated (api-contract CI).
