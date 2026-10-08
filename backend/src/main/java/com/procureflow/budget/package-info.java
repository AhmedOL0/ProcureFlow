/**
 * Budget management boundary.
 *
 * <p>Planned aggregates: budgets, budget allocations, reservations, spending.
 * Budget reservation happens inside the same transaction as purchase-request
 * approval (no distributed transactions). Materializes with the Budget
 * Management epic (Phase 4).</p>
 */
package com.procureflow.budget;
