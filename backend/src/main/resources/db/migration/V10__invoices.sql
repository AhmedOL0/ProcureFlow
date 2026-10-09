-- ============================================================
-- V10: invoices, lines and payments (Phase 5, epic 8 slice 1).
-- An invoice belongs to one sent (or further advanced) order; its lines
-- reference order lines by id for the 3-way match (ordered vs received
-- vs invoiced). Cumulative invoiced quantity per order line can never
-- exceed the ordered quantity. Payments accumulate to PAID; overpaying
-- is rejected.
-- ============================================================

CREATE TABLE invoices (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    order_id     UUID        NOT NULL REFERENCES purchase_orders (id),
    number       VARCHAR(50) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    total_minor  BIGINT      NOT NULL,
    currency     VARCHAR(3)  NOT NULL DEFAULT 'MAD',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_invoices_total CHECK (total_minor >= 0),
    CONSTRAINT uq_invoices_tenant_number UNIQUE (tenant_id, number)
);
CREATE INDEX ix_invoices_tenant_id ON invoices (tenant_id);
CREATE INDEX ix_invoices_order_id ON invoices (order_id);

CREATE TABLE invoice_lines (
    id               UUID         PRIMARY KEY,
    invoice_id       UUID         NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    order_item_id    UUID         NOT NULL,
    quantity         INTEGER      NOT NULL,
    unit_price_minor BIGINT       NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'MAD',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_invoice_lines_qty CHECK (quantity > 0)
);
CREATE INDEX ix_invoice_lines_invoice_id ON invoice_lines (invoice_id);
CREATE INDEX ix_invoice_lines_order_item_id ON invoice_lines (order_item_id);

CREATE TABLE invoice_payments (
    id           UUID        PRIMARY KEY,
    invoice_id   UUID        NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    amount_minor BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_invoice_payments_amount CHECK (amount_minor > 0)
);
CREATE INDEX ix_invoice_payments_invoice_id ON invoice_payments (invoice_id);
