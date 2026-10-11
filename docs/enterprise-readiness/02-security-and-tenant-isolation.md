# Security & tenant isolation

Method: static inspection + live probes (`GET /users/me` → 200; non-UUID var → 400). No intrusive testing, no external systems touched, no secrets printed. Severity with exploit preconditions.

## Critical

None proven. The highest-chain issue (unstuffed login + open join + no suspend) is rated High in aggregate below.

## High

### H1. No login/refresh/register throttling → credential stuffing
- Evidence: `backend/.../identity/application/AuthService.java` login/refresh paths; grep `RateLimit|Bucket4j|LoginAttempt` → zero hits; `PasswordPolicy.java:8` references a "rate-limited login" that does not exist. Only password-reset is throttled (`PasswordResetService.java:154-162`).
- Impact: unlimited password guessing against known demo-style emails; refresh endpoint equally open.
- Preconditions: network access to the API; no account lockout to stop an attacker.
- Remediation: per-IP + per-account token bucket (login 10/min, refresh 30/min, register 5/min), 429 with `Retry-After`, audit event on bucket trip. Reuse the reset-throttle pattern, not a new framework. Effort S. Priority P0.

### H2. Open workspace join by slug guessing
- Evidence: `AuthService.java:75-88` — known slug joins as MEMBER (read + `procurement:request`), any string creates a tenant as admin.
- Impact: unauthorized entry into workspaces with guessable slugs; tenant sprawl; slug-squatting phishing shape.
- Preconditions: knowledge/guess of a slug (slugs appear in URLs, emails, demos).
- Remediation: invite-token or admin-approval join; keep open-create for first workspace. Effort M. Priority P0. Until then, document as a known limitation for any shared demo.

### H3. No database backup/restore procedure
- Evidence: `docker-compose.yml:114-117` volumes only; grep `pg_dump|backup|restore` → no procedure; `docs/demo.md:28-58` has only a demo-reset snippet.
- Impact: single disk failure loses all tenants; no RPO/RTO exists.
- Remediation: nightly `pg_dump` sidecar + quarterly restore drill recorded in a runbook. Effort S. Priority P0.

### H4. Placeholder JWT secret boots with a public value
- Evidence: `application.yml:72` default is itself ≥64 chars, so the boot-length check (`JwtServiceImpl.java:31-39`) passes and tokens sign with a value published in the repo.
- Impact: any deployment that forgets `JWT_SECRET` mints forgeable tokens.
- Remediation: refuse the literal placeholder at boot (fail loudly). Effort S (one condition + test). Priority P0.

## Medium

### M1. Scheduler path is tenant-unscoped (defense in depth)
- Evidence: `EscalationScheduler.java:21-24` → `ApprovalService.java:164-172` global `findAllByDueAtBefore` → `advanceIfOverdue` uses `workflows.findById(id)` (`:210`) with no tenant check.
- Impact: a cross-tenant write primitive gated in practice by unguessable UUIDs — not remotely exploitable today, but one refactor away from incident. Fix is a one-line scope + test. Priority P0 (cheap correctness).

### M2. Access tokens survive password change/reset/logout (≤15 min)
- Evidence: `AuthService.java:150-162`, `UserAdminService.java:105-111`, `PasswordResetService.java:120` revoke refresh only.
- Impact: compromised session stays valid to expiry. Remediation: token version counter or denylist checked in `JwtAuthenticationFilter`. Priority P1. Effort M.

### M3. No suspension / role update / email verification
- Evidence: `User.java:145-149` statuses never written; `UserAdminService.java:83-88`; no verify code; roles set only at create.
- Impact: insider/compromised accounts cannot be disabled or demoted; fake emails hold sessions. Remediation: status endpoint + role-update endpoint (both audited) + verify-link gate. Priority P1. Effort M total.

### M4. Tokens in localStorage, body-carried refresh
- Evidence: `frontend/src/app/core/auth/auth.service.ts:20-27` (comment acknowledges XSS dependency).
- Impact: any XSS steals the full session. Mitigations in place: no `any`, no `innerHTML` observed, CSP missing though (see M6). Priority P1 (with CSP as the cheap half). Effort M for HttpOnly migration; S for CSP.

### M5. AI without quotas or 429 handling
- Evidence: `GroqAiProvider.java:78-86` (any failure → 502, no retry); metering without limiting (`README:172-173`); `AiIT` has no quota test.
- Impact: a compromised `ai:use` account burns provider budget; transient 429s surface as errors. Remediation: monthly per-tenant token cap + one retry with backoff. Priority P1. Effort M.

### M6. Missing security headers beyond HSTS/frame-deny
- Evidence: `SecurityConfig.java:47-49`; no CSP/Referrer-Policy/Permissions-Policy observed.
- Remediation: add + e2e-verify (frontend has no inline scripts, so CSP is viable). Priority P2. Effort S.

### M7. Audit redaction is one level deep
- Evidence: `AuditService.java:91-103` + `AuditRedactionTest` (top-level keys only).
- Impact: nested secret-bearing payloads persist to admin-readable rows. Remediation: recursive mask. Priority P2. Effort S.

## Low / Informational

- L1. No handlers for `ConstraintViolationException`, `HttpRequestMethodNotSupported`, `NoResourceFoundException` → framework defaults (status correct, envelope shape may differ). Add for envelope uniformity. Effort S.
- L2. JWT algorithm implicit (`signWith(key)` infers HS512 from length). Pin `HS512` explicitly to survive key-rotation refactors. Effort S.
- L3. `Stale AiProvider` javadoc claims no implementation exists. One-line doc fix.
- L4. Demo password in plaintext in `docs/demo.md` (demo-only, but rotate for shared demos).
- L5. No TLS in nginx config; HSTS lives in Spring. Fine behind a TLS terminator — document the assumption; do not add cert management to this repo.
- L6. `springdoc`/`swagger-ui` correctly disabled by default, enabled in dev/test. Verified good.
- L7. CSRF disabled with correct rationale for Bearer-header SPA; must be revisited if cookies ever land.

## Verified good (do not regress)

HS512 + boot-enforced secret length; required-claim validation; opaque refresh (SHA-256 at rest) + whole-chain theft revocation; generic login/reset responses; BCrypt-12; reset tokens (random, hashed, 30-min TTL, atomic consume, throttled); mail logs domain-only; audit masking + test; tenant-check-before-permission with 404s; `@PreAuthorize` on all mutations observed; CORS locked to `/api/**` with empty-origin lockdown; frontend never trusted for auth.
