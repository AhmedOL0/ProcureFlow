-- ============================================================
-- V13: password-reset tokens and throttle log (account management).
-- Reset tokens store only a SHA-256 hash; the raw value travels once,
-- inside the emailed reset URL. The throttle table backs per-email and
-- per-token rate limits without any external store.
-- ============================================================

CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);

CREATE TABLE password_reset_throttle (
    id           UUID        PRIMARY KEY,
    bucket       TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_reset_throttle_bucket ON password_reset_throttle (bucket, requested_at DESC);
