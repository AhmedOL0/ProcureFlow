import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

/**
 * Lazy feature boundaries: auth screens are public, everything else needs
 * a session. Feature modules (suppliers, procurement, approvals, budgets,
 * purchase-orders, invoices, analytics, ai, administration) land here next,
 * one lazy route each behind the guard.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    canActivate: [authGuard],
  },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: '**', redirectTo: 'dashboard' },
];
