# Initial backlog (Phase 2 seeds; each epic grows its own stories later)

Format: story → acceptance criteria → priority → technical notes.

## PF-1-1 Tenant registration
- As a company admin I can register my organization so that my team gets an isolated workspace.
- AC: unique slug enforced; creator becomes tenant admin; duplicate slug rejected with 409.
- Priority: Must. Notes: `organization` module, V2 migration, idempotent endpoint.

## PF-1-2 Login with JWT + refresh rotation
- As a user I can log in and stay logged in securely.
- AC: access token 15 min; refresh single-use rotated; reuse detected and session revoked; wrong password returns 401 without user enumeration.
- Priority: Must. Notes: `identity` module, BCrypt-12, Spring Security filter chain.

## PF-1-3 Role assignment
- As a tenant admin I can grant roles so that permissions follow the org chart.
- AC: roles scoped to tenant; permission check enforced on one endpoint + negative test.
- Priority: Must. Notes: method security, `role_permissions` join.

## PF-4-1 Submit purchase request (idempotent)
- As a requester I can submit a request with items so that approval can start.
- AC: replayed `Idempotency-Key` returns the original request, no duplicate; DRAFT → SUBMITTED transition validated.
- Priority: Must. Notes: `procurement` module, unique key on (tenant, idempotency_key).

## PF-5-1 First approval step
- As an approver I can approve or reject with a comment.
- AC: only the assigned approver (or delegate) can decide; decision is immutable once recorded; request status follows.
- Priority: Must. Notes: `approval` module, row lock on the request.

## PF-11-1 Copilot answers spend question (Phase 6)
- As a manager I can ask "who delivered best this quarter" and get an explained answer.
- AC: answer cites the underlying KPI queries; no raw PII leaves the tenant scope; usage metered.
- Priority: Should. Notes: `ai` module, Groq adapter, prompt versioning.
