-- ============================================================
-- V18: mailbox verification. New accounts start unverified; invite
-- redemption verifies (the invite link itself proves the mailbox).
-- Pre-existing rows predate verification and are grandfathered as
-- verified. Unverified accounts cannot mint password-reset links.
-- ============================================================

ALTER TABLE users ADD COLUMN verified BOOLEAN NOT NULL DEFAULT false;
UPDATE users SET verified = true;

CREATE TABLE email_verification_tokens (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_verification_tokens_user_id ON email_verification_tokens (user_id);
