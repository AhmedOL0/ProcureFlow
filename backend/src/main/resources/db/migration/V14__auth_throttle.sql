-- ============================================================
-- V14: auth throttle ledger (login/register/refresh rate limits).
-- Same shape as the password-reset ledger (V13): buckets hold hashed
-- account/IP material only, never emails or addresses. Kept separate
-- so auth and reset budgets never share a window.
-- ============================================================

CREATE TABLE auth_throttle (
    id           UUID        PRIMARY KEY,
    bucket       TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_throttle_bucket ON auth_throttle (bucket, requested_at DESC);
