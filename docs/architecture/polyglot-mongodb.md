# Polyglot persistence: MongoDB for AI extraction history

The only non-PostgreSQL store in ProcureFlow. Decided in ADR-012
(assessment + triggers); this page is the operator and reviewer guide.

## Responsibility matrix

| Store | Owns | Never owns |
|---|---|---|
| PostgreSQL | Tenants, users, roles, suppliers, requests, approvals, budgets, orders, invoices, payments, audit trail, AI metering | — (source of truth) |
| MongoDB `ai_extractions` | Versioned, validated extraction drafts + provenance (model, prompt version, input, timestamps, linked request id) | Money, approvals, ownership, anything PG computes |

## Data flow

```
POST /api/v1/ai/extract-request
  -> AiValidator.question -> quota -> Groq -> AiValidator.draft (gate)
  -> PG meter (same tx) -> Mongo save (best-effort) -> draft to caller
GET /api/v1/ai/extractions[ /{id}] -> Mongo, tenant-scoped, paged
POST /api/v1/ai/extractions/{id}/request (+ Idempotency-Key)
  -> ownership-checked load -> RequestDrafting port (same guarded,
     idempotent creation as POST /purchase-requests, X-Replay mirrored)
  -> extraction.createdRequestId linked (best-effort, heals on replay)
```

The copilot "Recent drafts" shelf lists history with per-row status:
unlinked rows offer creation (ai:use + procurement:request), linked rows
deep-link to their request. Draft prices stay proposals until the
guarded create path re-validates them — the UI never totals money.

Writes degrade (warn-once + `procureflow.ai.history_dropped`, answer
still returned); reads fail loud (503 `HISTORY_UNAVAILABLE`). Mongo down
never blocks an answer and never silently loses rows without the meter
firing (alert on it where receivers exist).

## Consistency and lifecycle

- No distributed transactions: the PG meter commits independently; the
  Mongo write follows, fire-and-forget inside the request thread.
- Retention: 90-day TTL on `createdAt` (asserted in IT).
- Indexes: `(tenantSlug, createdAt desc)` + TTL, both asserted in IT.
- Deletion: tenant deletion has no Mongo cascade today — expired docs
  age out via TTL; explicit per-tenant purge is a documented follow-up
  if a compliance trigger demands it.
- Rebuild: history is regenerable only by re-asking (prompts + inputs
  are the source); treat it as ephemeral insight, not record.

## Security

Tenant slug from the security principal on every read/write/query; id
reads include the slug (unknown and foreign ids are indistinguishable
404s); negative cross-tenant IT. No secrets or other tenants' data in
docs; local Mongo runs without auth (compose network only) — production
adds credentials + TLS, never committed.

## Run it

Compose dev includes `mongo` (default profile) with a ping healthcheck;
backend waits on it. `MONGODB_URI` overrides (default
`mongodb://localhost:27017/procureflow`, 2 s server-selection bound so
degraded answers stay fast). Tests use Testcontainers `mongo:8.0`
(same pin as compose). API types regenerate via `npm run gen:api`
after any endpoint change (api-contract CI enforces freshness).
