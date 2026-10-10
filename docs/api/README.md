# API conventions

The REST API is the product contract: Angular, future integrations and
the AI layer all consume it. OpenAPI is served live at `/v3/api-docs`
(Swagger UI at `/swagger-ui.html`).

## Rules (apply from the first endpoint in Phase 2)

- Versioning: all routes under `/api/v1/...`. No `/api/v2` exists yet —
  breaking changes will ship alongside v1, never as silent edits.
- Errors: consistent problem envelope `{ code, message, details?,
  traceId, timestamp }`; 4xx for client faults with actionable messages,
  never stack traces.
- Listing: endpoints return full workspace arrays (no pagination envelope
  yet — `page/size/sort` with `{ content, page, size, totalElements,
  totalPages }` is the documented target, not the current behavior).
- Money: integer minor units (`amountMinor`) plus ISO currency code.
- Time: UTC ISO-8601 (`Instant`) everywhere; no local times cross the wire.
- Idempotency: creation endpoints require `Idempotency-Key` (replays
  answer with the original result instead of duplicating work); other
  mutations are guarded by state machines and versioned decisions.
- Frontend types are generated from `/v3/api-docs` (Phase 3); keep
  `api-types.gen.ts` in sync by regenerating after every API change.
