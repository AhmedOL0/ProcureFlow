-- ============================================================
-- V17: workspace invites. Joining an existing tenant requires a live
-- invite for the registrant's address; creating a new tenant is unchanged.
-- Tokens store only a SHA-256 hash; the raw value travels once, inside
-- the invite link. Redemption is atomic and single-use.
-- ============================================================

CREATE TABLE invites (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    email        VARCHAR(320) NOT NULL,
    roles        TEXT        NOT NULL,
    token_hash   TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_by   UUID        NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_invites_tenant_id ON invites (tenant_id);
