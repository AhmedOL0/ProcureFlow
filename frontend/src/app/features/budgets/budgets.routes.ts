import { Routes } from '@angular/router';

/** Budgets & spend, lazy under the shell. */
export const BUDGET_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./budget-list/budget-list.component').then((m) => m.BudgetListComponent),
    data: { breadcrumb: 'Budgets' },
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./budget-detail/budget-detail.component').then((m) => m.BudgetDetailComponent),
    data: { breadcrumb: 'Budget detail' },
  },
];
