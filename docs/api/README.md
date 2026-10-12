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
- Listing: directory endpoints return `Paged` envelopes
  (`page/size` with `{ content, page, size, totalElements, totalPages }`,
  size 1–100, out-of-range answers 400); unbounded full-array reads remain
  only on small reference/admin paths.
- Money: integer minor units (`amountMinor`) plus ISO currency code.
- Time: UTC ISO-8601 (`Instant`) everywhere; no local times cross the wire.
- Idempotency: creation endpoints require `Idempotency-Key` (replays
  answer with the original result instead of duplicating work); other
  mutations are guarded by state machines and versioned decisions.
- Frontend types are generated from `/v3/api-docs` (Phase 3); keep
  `api-types.gen.ts` in sync by regenerating after every API change.

## Documents: import & export

Binary endpoints (filenames via `Content-Disposition: attachment`, exposed
over CORS so cross-origin downloads keep the server name):

| Endpoint | Gate | Result |
|----------|------|--------|
| `GET /suppliers/export.xlsx?status=&q=` | any member | Filtered directory (max 5000 rows, else 400 `EXPORT_TOO_LARGE`) |
| `POST /suppliers/import` (multipart `file`) | `supplier:write` | 200 `{ created, errors: [] }`, or 400 with flattened `row.N.field` details |
| `GET /purchase-orders/{id}/document.pdf` | `order:read` | Printable order (supplier, lines, total) |
| `GET /purchase-orders/export.xlsx?status=` | `order:read` | Filtered orders (max 5000) |
| `GET /invoices/{id}/document.pdf` | `invoice:read` | Invoice with paid/balance |
| `GET /invoices/export.xlsx?status=&q=` | `invoice:read` | Filtered invoices with paid/balance (max 5000) |
| `GET /purchase-requests/export.xlsx?status=&q=` | any member | Workspace requests with requester + total (max 5000) |
| `GET /analytics/spend/report.pdf?period=` | `analytics:read` | Spend report over the page aggregates |
| `GET /analytics/spend/report.xlsx?period=` | `analytics:read` | Same as 4-sheet workbook |

Import contract (`name` required; `email, phone, tax_id, address, status,
categories` optional; headers case-insensitive; unknown columns ignored;
status defaults to active; categories are `;`-separated catalog names):

- Only `.xlsx`/`.csv`, sniffed by content (zip magic / text), never by
  extension alone; 5 MB and 500 data rows per file.
- All-or-nothing: any row failure rejects the file with 400
  `IMPORT_VALIDATION_FAILED` and imports zero suppliers — fix the rows and
  re-upload. Row numbers count data rows.
- Every import and every generated document writes an audit row
  (`SUPPLIER_IMPORTED`, `DOCUMENT_EXPORTED`).
- All exported strings pass the formula-injection guard (leading
  `= + - @ | %` takes the spreadsheet text marker).
