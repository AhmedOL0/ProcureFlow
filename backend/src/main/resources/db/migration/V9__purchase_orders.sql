-- ============================================================
-- V9: purchase orders and lines (Phase 5, epic 7 slice 1).
-- An order is born from exactly one APPROVED request (unique link) with
-- its lines snapshotted; the supplier link is mandatory, so deleting a
-- referenced supplier fails until the order is gone. received_qty tracks
-- goods receipt per line for the invoice 3-way match.
-- Lifecycle: DRAFT → SENT → PARTIALLY_RECEIVED → RECEIVED → CLOSED,
-- with CANCELLED from DRAFT or SENT.
-- ============================================================

CREATE TABLE purchase_orders (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    request_id  UUID        NOT NULL REFERENCES purchase_requests (id),
    supplier_id UUID        NOT NULL REFERENCES suppliers (id),
    status      VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    currency    VARCHAR(3)  NOT NULL DEFAULT 'MAD',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_orders_request UNIQUE (request_id)
);
CREATE INDEX ix_purchase_orders_tenant_id ON purchase_orders (tenant_id);
CREATE INDEX ix_purchase_orders_supplier_id ON purchase_orders (supplier_id);

CREATE TABLE purchase_order_items (
    id               UUID         PRIMARY KEY,
    order_id         UUID         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    description      VARCHAR(500) NOT NULL,
    category         VARCHAR(200),
    quantity         INTEGER      NOT NULL,
    unit_price_minor BIGINT       NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'MAD',
    received_qty     INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_purchase_order_items_qty CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_received CHECK (received_qty >= 0)
);
CREATE INDEX ix_purchase_order_items_order_id ON purchase_order_items (order_id);
