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
- Default roles, seeded per tenant and backfilled additively (existing
  tenants gain new roles without losing grants):

  | Role | Least-privilege grants | Persona |
  |---|---|---|
  | `TENANT_ADMIN` | all 17 permissions | tenant admin |
  | `APPROVER` | `procurement:request`, `procurement:approve`, `supplier:read`, `order:read`, `invoice:read`, `budget:read`, `analytics:read` | approver / manager |
  | `FINANCE` | `budget:read`, `budget:manage`, `invoice:read`, `invoice:write`, `order:read`, `supplier:read`, `analytics:read` | finance |
  | `OFFICER` | `supplier:read/write`, `procurement:request`, `order:read/write`, `invoice:read/write`, `budget:read`, `analytics:read` | procurement officer |
  | `AUDITOR` | `audit:read`, `analytics:read`, `supplier:read`, `order:read`, `invoice:read`, `budget:read` | auditor (read-only) |
  | `MEMBER` | `procurement:request`, `supplier:read`, `order:read`, `invoice:read`, `analytics:read` | requester |

  `ai:use` stays `TENANT_ADMIN`-only while `AI_ENABLED=false`. Roles are
  assigned at user creation (`POST /users roleNames[]`); no role-update
  endpoint exists.

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

## Phase 2 state

Login, registration (which provisions or joins a workspace), single-use
refresh rotation with theft detection, BCrypt-12 hashing, method-level RBAC
and the tenant propagation filter are implemented and covered by
integration tests. The JWT secret refuses to boot under 64 characters.
OAuth2/social login stays out of scope; refresh via HttpOnly cookie (web)
versus body token (mobile) is decided with the first client.
