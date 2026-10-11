# Enterprise feature roadmap

Staging: MVP (now) → First-tenant hardening (P0/P1) → Growth (P2) → Enterprise tier (P3). Each item answers: problem, users, release necessity, architecture fit, risks, complexity, evidence. Dependencies first; priority is not order.

## Phase 1 — first-tenant hardening (P0/P1)

1. **Login/refresh/register throttling** — stops credential stuffing; all users. Required for any exposed deploy. Fits existing throttle pattern. S. No deps.
2. **Invite-gated join + suspend + role update + email verification** — makes workspaces operable (HR join/leaver, least privilege). Required for real B2B. Fits services + audit. M. Deps: mail delivery live.
3. **Live Brevo proof** — resets must arrive. Required. S. Deps: confirmed sender/key (blocked on credentials, pre-existing).
4. **Pagination envelopes** (audit/notifications/requests first) — unbounded reads are the first outage shape. Required. M. No deps (documented target already).
5. **Scheduler tenant scoping + 4 composite indexes + V14** — closes unscoped write path; fixes month-end queries. Required. S. No deps.
6. **Backup sidecar + restore drill + runbook** — RPO/RTO exist. Required. S. No deps.
7. **Per-tenant AI caps + 429/retry** — cost abuse control. Essential. M. No deps.
8. **Missing race tests** (invoice book/pay, order receive) + authority-matrix test + axe CI + Firefox CI. Essential quality. S. No deps.

## Phase 2 — growth (P2)

- Notification email wiring + preferences + bulk-read endpoint + retry. (Approvers miss decisions today.) M. Deps: mail live.
- Audit coverage for supplier/request/delegation/workflow/AI surfaces; recursive redaction; outbox for guaranteed delivery. M. No deps.
- CSV exports (tenant-scoped) for requests/invoices/audit; audit export for compliance. M. Deps: pagination.
- Amend/reopen + return-for-changes + optional self-approval ban (policy flag). M. Deps: none.
- Cumulative cross-invoice 3-way match + credit notes. M. Deps: none.
- Order numbering + 404 page + saved filters + multi-currency rule (buckets or declared single-currency). S/M. Deps: none.
- Observability: Prometheus/Alertmanager in compose, request-id MDC, structured-log option. M. No deps.
- N+1 bulk-fetch rewrites with query-count tests. M. Deps: pagination.

## Phase 3 — enterprise tier (P3, defer until a deal requires)

- **SSO/SAML/OIDC + SCIM**: needed when buyers mandate corporate identity; password-auth is fine for MVP. XL. Evidence required: a customer RFP asking for it.
- **ERP/accounting integrations + webhooks + service credentials**: pull, not push — build the first connector against a named system. L/XL.
- **Supplier portal (quotation submission), compliance docs, onboarding workflows**: genuine lifecycle depth for regulated buyers. L.
- **Attachments/documents**: needs storage, AV scanning, access-control design — do not bolt on local-disk uploads. M/L.
- **i18n/l10n, branding, feature flags, tenant quotas/SLOs**: scale-stage concerns. M.
- **Microservices/Kafka/K8s**: explicitly not recommended — no evidenced need; the monolith has headroom (connection pool + pagination + indexes come first).

## Deliberately out of scope

Real money movement (payment gateway): the product correctly tracks payment *status* only — say so in sales copy. Per-tenant schemas/encryption: operational cost without a compliance driver. Multi-provider AI: no availability/privacy/cost requirement evidenced.
