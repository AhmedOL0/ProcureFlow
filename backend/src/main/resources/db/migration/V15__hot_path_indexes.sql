-- ============================================================
-- V15: composite indexes for hot query paths + the missing
-- invoice-line foreign key. All tables are small in practice, so plain
-- CREATE INDEX (no CONCURRENTLY; Flyway runs in a transaction) is fine.
-- 1. budgets(tenant_id, period): serves lockPeriodBudgets on every
--    approval carrying a reservation.
-- 2. purchase_requests(tenant_id, status): serves the directory and
--    inbox status filters.
-- 3. supplier_category_links(category_id): reverse leg of the PK for
--    category→supplier scans (in-use guard, listings).
-- 4. invoice_lines.order_item_id FK: the column had only an index; code
--    validates in the service, but the database should enforce it so a
--    future line-delete path cannot orphan invoice lines.
-- ============================================================

CREATE INDEX ix_budgets_tenant_period ON budgets (tenant_id, period);
CREATE INDEX ix_purchase_requests_tenant_status ON purchase_requests (tenant_id, status);
CREATE INDEX ix_supplier_category_links_category ON supplier_category_links (category_id);
ALTER TABLE invoice_lines
    ADD CONSTRAINT fk_invoice_lines_order_item FOREIGN KEY (order_item_id) REFERENCES purchase_order_items (id);
