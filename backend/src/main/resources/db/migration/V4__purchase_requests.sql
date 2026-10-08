-- ============================================================
-- V4: purchase requests and items.
-- One idempotency key per tenant: replays return the original request
-- instead of duplicating it (unique constraint is the real guard; the
-- service translates the race into a replay). Item supplier links are
-- RESTRICTed: deleting a referenced supplier fails until the purchase
-- order epic adds an explicit reference guard with a 409.
-- ============================================================

CREATE TABLE purchase_requests (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    requester_id    UUID         NOT NULL REFERENCES users (id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    priority        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    idempotency_key VARCHAR(100) NOT NULL,
    submitted_at    TIMESTAMPTZ,
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_requests_tenant_key UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX ix_purchase_requests_tenant_id ON purchase_requests (tenant_id);
CREATE INDEX ix_purchase_requests_requester_id ON purchase_requests (requester_id);

CREATE TABLE purchase_request_items (
    id               UUID         PRIMARY KEY,
    request_id       UUID         NOT NULL REFERENCES purchase_requests (id) ON DELETE CASCADE,
    description      VARCHAR(500) NOT NULL,
    category         VARCHAR(200),
    quantity         INTEGER      NOT NULL,
    unit_price_minor BIGINT       NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'MAD',
    supplier_id      UUID         REFERENCES suppliers (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_purchase_request_items_request_id ON purchase_request_items (request_id);
