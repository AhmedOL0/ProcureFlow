import { Routes } from '@angular/router';

/**
 * Feature modules arrive here as lazy boundaries, one per business area:
 * dashboard, procurement, suppliers, approvals, budgets, purchase-orders,
 * invoices, analytics, ai, administration. Example (Phase 3):
 *
 *   { path: 'suppliers', loadChildren: () => import('./features/suppliers/suppliers.routes').then(m => m.SUPPLIER_ROUTES) }
 */
export const routes: Routes = [];
