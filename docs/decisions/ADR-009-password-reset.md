# ADR-009: Email-link password resets

- Status: Accepted (account-management slice)
- Context: Users lock themselves out; admins need a helpdesk reset; there
  is no SMTP in the stack yet and emails are not unique across tenants.
- Decision:
  - `POST /api/v1/auth/forgot-password` and `POST /api/v1/auth/reset-password`
    live under `/api/v1` like every other endpoint (the proposed `/api/...`
    prefix would fork the contract and force SecurityConfig surgery for no
    benefit — `/api/v1/auth/**` is already public).
  - Tokens are 32 cryptographically random bytes, SHA-256 at rest, 30-min
    TTL, single-use via an atomic conditional UPDATE (concurrent redemption
    admits exactly one winner). Forgot answers are identical for known,
    unknown and ambiguous accounts.
  - Email alone is ambiguous across tenants, so the request accepts an
    optional `tenantSlug` (the login disambiguation pattern); without it,
    exactly-one-match proceeds, otherwise nothing is minted.
  - Rate limiting is Postgres-backed (`password_reset_throttle`: 5/hour per
    email scope, 10/10min per token), not Redis — it works in every profile
    including tests, with zero new infrastructure.
  - Mail goes through a `MailPort` with a Brevo SMTP adapter and a log-mode
    sink (default). The log sink never records tokens or URLs; tests use a
    mocked port, never live SMTP.
  - Reset success revokes every refresh session of the target.
  - Email *changes* are out of scope: no verified-address workflow exists,
    and trusting an unverified address would break login disambiguation.
  - Tenant preferences are out of scope: no such domain entity exists.
- Consequences: Forgot/reset works the day Brevo credentials land in env;
  until then the flow is fully testable offline. A second provider means
  one new `MailPort`, not a rewrite.
