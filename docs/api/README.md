# API conventions

The REST API is the product contract: Angular, future integrations and
the AI layer all consume it. OpenAPI is served live at `/v3/api-docs`
(Swagger UI at `/swagger-ui.html`).

## Rules (apply from the first endpoint in Phase 2)

- Versioning: all routes under `/api/v1/...`. Breaking changes ship as
  `/api/v2` alongside v1, never as silent edits.
- Errors: consistent problem envelope `{ code, message, details?, traceId }`;
  4xx for client faults with actionable messages, never stack traces.
- Pagination: `page/size/sort` query params, envelope
  `{ content, page, size, totalElements, totalPages }`.
- Money: integer minor units (`amountMinor`) plus ISO currency code.
- Time: UTC ISO-8601 (`Instant`) everywhere; no local times cross the wire.
- Idempotency: every mutating client request carries `Idempotency-Key`;
  replays return the original result instead of duplicating work.
- Frontend types are generated from `/v3/api-docs` (Phase 3); hand-written
  DTO duplicates are a CI failure once generation lands.
