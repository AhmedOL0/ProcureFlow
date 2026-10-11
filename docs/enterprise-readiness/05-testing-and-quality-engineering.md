# Testing & quality engineering

Counts verified by inspection this session; results re-executed (see bottom).

## Backend: 127 @Test + 4 @ArchTest = 131 checks, 20 classes

- Integration (Testcontainers Postgres, per-class container, random port): AccountIT 16, ApprovalIT 16, AuthFlowIT 10, BudgetIT 10, OrderIT 8, InvoiceIT 7, ProcurementIT 7, SupplierIT 7, AiIT 6 (+FakeAiProvider), NotificationIT 6, AnalyticsIT 5, AuditIT 5, TenantIsolationIT 4, RoleProvisioningIT 4, InvalidPathVariableIT 2, MetricsIT 2. Total 115.
- Unit (no Spring): JwtServiceTest 8, CopilotServiceTest 2, AuditRedactionTest 2. Total 12.
- ArchUnit: 4 rules (api→infrastructure, shared→modules, no field injection, domain→Spring).
- Strengths: ~13 cross-tenant negatives (404 convention), 5 two-thread race tests (decide, budget hold, order create, idempotent create, reset redemption), auth-matrix tests per persona, theft-response test, malformed-JSON 400 test, per-tenant AI metering test.
- Gaps: no invoice-booking/pay race, no order-receive race, no contact-primary race; no per-route authority matrix; no quota/429 AI tests; no `@ParameterizedTest` anywhere (matrix tests written longhand); error-envelope shape asserted ad hoc per test, not via a shared helper.

## Frontend: 28 Vitest + 19 Playwright

- Vitest (node env, pure TS, no TestBed): api-errors 3, time 2, names 4, format 4, money 2, auth-tokens 4, status-badge 2, spend-categories 3, supplier-rows 4. No component tests, no guard/interceptor tests, no form-validation tests.
- Playwright (Chromium only, stubbed `**/api/v1/**` doubles, unmatched→abort): smoke 1, auth 7, journeys 11 covering register→request→approve→budget→order→invoice→admin→analytics→copilot→notifications→settings end to end at contract level.
- Gaps: no live-backend journey in CI (manual only), no axe/a11y, no Firefox/WebKit, no visual regression, no TestBed coverage of guards/interceptors/dialogs.

## 13 business scenarios mapped

1 Onboarding ✔ (`AuthFlowIT:31,55,73` + e2e org-admin). 2 Login/expiry ✔ (`AuthFlowIT:104,144,158,183` + e2e). 3 Suppliers ✔ (`SupplierIT` + e2e). 4 Request submit/idempotency ✔ (`ProcurementIT:38,54,66,97` + e2e). 5 Approve/reject/delegate/escalate ✔ (`ApprovalIT` 16 + e2e). 6 Budget reserve/release ✔ (`BudgetIT:41,61,97,155,198` + e2e creation). 7 Orders ✔ (`OrderIT` + e2e). 8 Invoices ✔ (`InvoiceIT` + e2e). 9 Notifications+audit ✔ (`NotificationIT`, `AuditIT`, e2e mark-read). 10 Recovery ✔ (`AccountIT` 16). 11 Roles+isolation ✔ (`RoleProvisioningIT`, `TenantIsolationIT`, per-module negatives). 12 AI extract ✔ (`AiIT:53`, e2e stubbed). 13 AI failure/quota: partial ✔ (503 paths covered; quotas/429 missing).

## Recommended gates (in priority order)

1. Keep `verify` as the merge gate (it is the gate today — backend-ci runs it). P0 (already true).
2. Add missing race tests (invoice book/pay, order receive) + authority-matrix test + quota test. P1/S-M.
3. Add axe checks + Firefox to frontend-ci; add api-types regenerate-and-diff job. P1/S.
4. Add a quarterly live-backend + live-AI journey runbook (manual, recorded) since CI deliberately stubs. P2/S (docs).
5. Consider shared envelope-assert helper + parameterized matrices to cut test-maintenance cost. P3.

## Fresh results (2026-10-10, this audit)

- `./mvnw.cmd -B verify`: 115 IT + 16 unit/arch, 0 failures, coverage floors (0.70/0.40) met, BUILD SUCCESS (2m21s).
- `npm run build`: green. `npm test`: 28/28. `npm run test:e2e`: 19/19 Chromium.
- Live probes: login → 200; `GET /users/me` → 200; `/users/not-a-uuid` → 400 `INVALID_PATH_VARIABLE`.
