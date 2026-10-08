# Security design

## Authentication

- Username/password login issues a short-lived JWT access token (15 min)
  plus a rotating refresh token (server-side, single-use, theft-detected).
- Passwords hashed with BCrypt (cost factor 12); no plaintext anywhere,
  including logs.
- OAuth2/social login is out of scope for Phase 1; the design does not
  preclude adding it behind the same session model.

## Authorization

- RBAC: users get roles, roles bundle fine-grained permissions
  (for example `procurement:approve`, `supplier:write`).
- Permissions are checked server-side on every endpoint (method security);
  the frontend hides UI but never enforces.
- Tenant check runs before permission check: wrong tenant always wins
  over right role.

## Transport and headers

- TLS terminated at the edge in production; cookies (refresh token) are
  `HttpOnly`, `Secure`, `SameSite=Lax`. Access token travels as
  `Authorization: Bearer` (Angular attaches it via interceptor).
- Secure response headers (HSTS, frame options, content-type options)
  configured in Phase 2 with the security filter chain.

## Data protection

- PII minimization: store only what procurement needs; audit payloads
  exclude secrets and password hashes.
- Secrets exclusively via environment variables (see
  `docs/security/secrets.md`); `.env` is git-ignored and CI uses
  repository secrets.

## Phase 1 state

`spring-boot-starter-security` is on the classpath (fails closed with a
generated password until the filter chain lands). Real login, RBAC and
tenant filters are the Identity & Access epic (Phase 2).
