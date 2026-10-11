# Feature completeness matrix

Status: Verified / Partially verified / Unverified / Missing / Not applicable (see `00-executive-summary.md` for method). Risk: Critical/High/Medium/Low/Informational. Priority: P0/P1/P2/P3 (P0 = first-paying-tenant blocker).

## A. Identity, accounts, organizations

| Capability | Status | Evidence | Impact if incomplete | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| Register + onboarding (provision-or-join) | Verified | `AuthService.java:69-95`, `AuthFlowIT:31,55,73` | — | — | — | — | — | none | — |
| Login/logout, generic errors, LOGIN_AMBIGUOUS | Verified | `AuthService.java:97-119`, `AuthFlowIT:104` | — | — | — | — | — | none | — |
| Refresh rotation + reuse revokes chain | Verified | `AuthService.java:121-138`, `AuthFlowIT:158` | — | — | — | — | — | none | — |
| Login throttling/lockout | Missing | no `RateLimit\|Bucket4j\|LoginAttempt` in main | credential stuffing | High | P0 | — | S | per-IP+account bucket, 429 + audit event | 100 bad logins/min/IP → 429, no valid-login impact |
| Password change kills sessions | Verified | `AuthService.java:150-162`, `AccountIT:193` | — | — | — | — | — | none | — |
| Email-link reset (hashed, 30-min TTL, atomic consume, throttled) | Verified | `PasswordResetService.java`, `AccountIT:69-183` | — | — | — | — | — | none | — |
| Brevo delivery operational | Partially verified | `SmtpMailPort.java`, log-mode default; live 535-blocked | resets never arrive | High | P0 | confirmed sender/key | S | confirm sender identity + key, send live test | live reset email received in test mailbox |
| Email verification | Missing | no verify code; tokens issued at register | fake emails hold sessions | Medium | P1 | mail delivery | M | verify-link on register, gate sensitive actions | unverified account cannot reset/approve |
| Account suspension/deactivation | Missing | `INVITED,DISABLED` never written; `UserAdminService:83-88` | cannot eject insider/compromised account | Medium | P1 | — | S | `PATCH /users/{id}/status` + tests | disabled user 401s on next call, refresh dead |
| Role update after creation | Missing | roles only at register/create | over-privilege persists | Medium | P1 | audit it | S | `PATCH /users/{id}/roles` (admin, audited) | role change effective on next token, audit row |
| Invitations/onboarding flow | Missing | `INVITED` unreferenced | open-join risk (below) | High | P0 | — | M | invite tokens or admin approval for joins | unknown party cannot join by slug guessing |
| Open slug join/create | Verified (by design) | `AuthService.java:75-88` | unauthorized workspace entry, sprawl | High | P0 | invites | M | see above | — |
| Access-token revocation | Missing | only refresh revoked (`AuthService:140-162`) | 15-min post-compromise window | Medium | P1 | — | M | token version/jti blocklist or short TTL + sliding | changed-password token rejected <60s |
| SSO/OIDC/SAML | Missing | no hits in main | — | Info | P3 | — | XL | defer until first enterprise deal requires it | — |
| Concurrent-session handling | Partially verified | per-token revoke works; no device list | user cannot see/kill sessions | Low | P2 | — | M | session list + per-session revoke | — |

## B. Multi-tenancy & RBAC

| Capability | Status | Evidence | Impact | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| TenantContext set post-auth, cleared in finally | Verified | `TenantFilter.java:21-36`, `SecurityConfig.java:69` | — | — | — | — | — | none | — |
| Service-level scoping + cross-tenant 404 | Verified | `UserAdminService:54-60`, `TenantIsolationIT` (4) + ~9 module isolation tests | — | — | — | — | — | none | — |
| Isolation IT for suppliers/orders/invoices/analytics/AI/notifications | Partially verified | per-module `*InvisibleAcrossTenants` exist; inbox-list/supplier-perf edge untested | unproven corners | Medium | P1 | — | S | add 3-4 negative tests | each module has a cross-tenant read+write test |
| Scheduler tenant scoping | Partially verified | `EscalationScheduler:21-24` → unscoped `findById` (`ApprovalService:210`) | cross-tenant write path (UUID-gated) | Medium | P0 | — | S | `findByIdAndTenantId` + test | scheduler test with two tenants, no cross-mutation |
| Permission catalog + 6 roles, additive provisioning | Verified | `PermissionCodes.java`, `RoleProvisioningIT` (4) | — | — | — | — | — | none | — |
| Per-route authority matrix test | Fixed 2026-10-11 | `AuthorizationMatrixIT` (10 routes × member/admin/anonymous) + `ArchitectureGatesTest.controllersEnforceAuthorization` | silent privilege drift | Medium | P1 | — | S | done | CI fails on new unannotated route |
| Backend authoritative (frontend hints only) | Verified | `auth.service.ts:44-71` comment, shell gating | — | — | — | — | — | none | — |

## C–H. Procurement lifecycle

| Capability | Status | Evidence | Impact | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| Supplier CRUD + duplicate guard + delete guard + categories + scorecards | Verified | `SupplierService`, `SupplierIT` (7) | — | — | — | — | — | none | — |
| Supplier archiving lifecycle | Missing | hard delete only; status field unused for lifecycle | history loss on delete | Low | P2 | — | S | `ARCHIVED` state + filter | — |
| Supplier onboarding/verification/compliance docs | Missing | no fields/endpoints | not a real lifecycle for regulated buyers | Medium | P2 | attachments | M | verification state + doc expiry | — |
| Request lifecycle enforced server-side + idempotent create + race test | Verified | `PurchaseRequestService`, `ProcurementIT` (7) | — | — | — | — | — | none | — |
| Amend/reopen (rejected→draft) | Missing | no transition | users clone by hand | Low | P2 | audit it | S | `POST /{id}/reopen` from REJECTED/CANCELLED | — |
| Configurable lanes + delegation/expiry + escalation (both paths) | Verified | `WorkflowService`, `ApprovalService`, `ApprovalIT` (16) | — | — | — | — | — | none | — |
| Self-approval prevention | Missing | no decider≠requester check | requester with approve rights signs own spend | Medium | P1 | policy flag | S | optional `forbidSelfApproval` on lane + test | self-decide → 409 when enabled |
| Return-for-changes verdict | Missing | only APPROVED/REJECTED | binary outcomes, no rework loop | Medium | P1 | reopen | M | `CHANGES_REQUESTED` → back to DRAFT | — |
| Budget reserve/release atomic with decide/cancel + overspend 409 + race test | Verified | `BudgetService`, `BudgetIT` (10) | — | — | — | — | — | none | — |
| Budget transfers/adjustments, department pots | Missing | `departmentId` reserved, no transfer | rigid pots | Low | P2 | — | M | transfer endpoint or documented no | — |
| Multi-currency conversion | Missing | mismatch rejected, sums assume single currency | wrong totals if mixed | Medium | P1 | — | M | per-currency buckets or declared single-currency rule | mixed-currency workspace cannot mis-add |
| Orders: approved-only, snapshots, send/receive/close/cancel, duplicate guard | Verified | `OrderService`, `OrderIT` (8) | — | — | — | — | — | none | — |
| Order numbering / PDF | Missing | UUID only, no PDF | unprofessional documents | Low | P2 | — | M | sequential per-tenant numbers | — |
| Invoices: booking rules, over-invoice/overpay guards, 3-way match view, duplicate numbers | Verified | `InvoiceService`, `InvoiceIT` (7) | — | — | — | — | — | none | — |
| Cumulative cross-invoice match, credit notes/void | Missing | match is per-invoice | partial picture, no corrections | Medium | P2 | — | M | cumulative match; credit-note type | — |
| Invoice/order concurrency tests | Missing | locks exist, races unproven | overspend/overpay under race | Medium | P1 | — | S | 2-thread tests mirroring BudgetIT | red-before/green-after recorded |
| Idempotency beyond request creation | Missing | key only on requests | double-click orders/payments | Medium | P1 | — | M | keys on orders/invoices/payments | replay returns original, one row |

## I–J. Notifications, audit

| Capability | Status | Evidence | Impact | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| Inbox (submit/decide events, AFTER_COMMIT, read flags) | Verified | `NotificationListener`, `NotificationIT` (6) | — | — | — | — | — | none | — |
| Email delivery of notifications | Missing | logging skeleton by design | users miss approvals | Medium | P1 | mail delivery | M | wire MailPort to real events | approval email arrives <2 min |
| Preferences, retry/DLQ, bulk read | Missing | no prefs/retry; N+1 mark-all-read | toil, missed events | Low | P2 | — | S/M | prefs + bulk endpoint | — |
| Audit events for decisions/orders/invoices/budgets with before/after + redaction | Verified | `AuditService`, `AuditIT` (5), `AuditRedactionTest` | — | — | — | — | — | none | — |
| Audit for supplier/request/delegation/workflow/AI changes | Missing | no emitters | compliance blind spots | Medium | P1 | — | M | emit on the 6 missing surfaces | each surface has an emit test |
| Audit drop-on-failure (catch-and-log) | Partially verified | `AuditService:45-56` | silent audit gaps | Medium | P2 | outbox | M | outbox or reconcile job | failure replays, no gaps |
| DB-level append-only enforcement | Unverified | code-level only | tamperable by DB access | Low | P2 | — | S | revoke update/delete grants | — |

## K. Search, filtering, exports, UX plumbing

| Capability | Status | Evidence | Impact | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| Server filters + client sort/page, all four data states | Verified | every list component + html | — | — | — | — | — | none | — |
| Pagination envelopes | Missing | zero `Pageable` in backend | unbounded reads break at scale | High | P0 | — | M | envelope per `docs/api/README.md` target | 10k-row table loads page 1 <500ms |
| CSV/export, PDF reports | Missing | no export code | ops toil, audit needs | Medium | P2 | pagination | M | tenant-scoped CSV for requests/invoices/audit | export of foreign tenant → 404 |
| Bulk operations | Missing | N+1 inbox reads noted | toil at scale | Low | P3 | — | M | — | — |
| 404 page | Missing | `**` redirects to dashboard/login | confused navigation | Low | P2 | — | S | NotFound component | — |

## AI capabilities

| Capability | Status | Evidence | Impact | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| Gated provider (503 when disabled), timeouts, 502 mapping | Verified | `GroqAiProvider`, `CopilotServiceTest` | — | — | — | — | — | none | — |
| Tenant-scoped aggregates-only prompts + citations | Verified | `CopilotService:59-137`, `AiIT` | — | — | — | — | — | none | — |
| Output validation (bounds, extract JSON schema) | Verified | `AiValidator:23-80` | — | — | — | — | — | none | — |
| Per-tenant quotas / spend caps | Fixed 2026-10-11 | monthly token cap (`app.ai.monthly-token-cap`, quota unit tests) | AI cost abuse | Medium | P1 | — | M | done | capped tenant gets 429 AI_QUOTA_EXCEEDED |
| 429/retry/backoff on provider calls | Fixed 2026-10-11 | retry-once on 429/5xx, 429→PROVIDER_RATE_LIMITED (`GroqAiProviderTest`) | transient failures surface raw | Medium | P2 | — | S | done | — |
| Prompt-injection sanitization | Missing | length-check + concat only | exfiltration via crafted data | Low | P2 | — | S | delimit/escape user content | injection test in AiIT |
| Conversation history storage | Missing | no history table | no continuity/audit of chats | Low | P3 | — | M | — | — |
| Stale `AiProvider` javadoc ("no implementation") | Verified (comment) | `AiProvider.java:4-9` | doc rot | Info | P3 | — | S | one-line fix | — |

## Cross-cutting engineering

| Capability | Status | Evidence | Impact | Risk | Priority | Deps | Effort | Action | Acceptance |
|---|---|---|---|---|---|---|---|---|---|
| ArchUnit module boundaries (4 rules) | Verified | `ArchitectureRulesTest`, green | — | — | — | — | — | none | — |
| Outbox for domain events | Missing | in-process Spring events only | lost notifications on crash between commit and listener | Medium | P2 | — | M | outbox table + relay | kill-9 test: no lost event |
| Missing composite indexes (4) | Fixed 2026-10-11 | V15 migration (budgets, requests, links, invoice FK) | slow month-end queries | Medium | P1 | — | S | done; EXPLAIN on hot paths | seq-scan-free on hot paths |
| N+1 fan-outs (budget sums, invoice lines, approval steps, analytics) | Verified | §3 data report | latency at scale | Medium | P1 | pagination | M | bulk-fetch rewrites | query-countasserted tests |
| Structured logs / correlation IDs | Missing | text logs, traceId only on 500s | un-debuggable incidents | Medium | P2 | — | S | MDC request id + JSON option | — |
| Prometheus wired + alerts firing | Partially verified | configs + `alerts.yml` exist, unwired | blind ops | Medium | P1 | compose service | M | add service, receiver, drill | alert fires on synthetic 5xx |
| pg backup + restore test | Missing | volumes only | data loss | High | P0 | — | S | nightly dump sidecar + quarterly restore drill | restore rehearsal recorded |
| Secrets: env-only, fail-fast compose | Verified | `docker-compose.yml:81`, `secrets.md` | — | — | — | — | — | none | — |
| Placeholder JWT boots with public value | Disproven 2026-10-11 | default is 61 chars; length gate refuses it (`JwtServiceTest.documentedPlaceholderRefusesToStart`) | none — boot already fails loudly | Info | — | — | — | none | — |
| CSP / Referrer-Policy / Permissions-Policy | Missing | HSTS + frame-deny only | XSS impact uncontained | Low | P2 | — | S | add headers, verify e2e | — |
| Frontend axe tests | Fixed 2026-10-11 | `e2e/a11y.spec.ts` (5 pages, wcag2a+aa) caught + fixed a critical chip-role violation | a11y regressions undetected | Medium | P1 | — | S | done | — |
| valueChanges unsubscribe hygiene | Partially verified | ~12 never-torn-down subs | slow leaks on navigation | Medium | P2 | — | S | takeUntilDestroyed/async | — |
| api-types freshness proof | Unverified | no timestamp; spot-checks match | silent drift | Low | P2 | — | S | CI diff job regenerate-and-compare | — |
