import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

/**
 * Public auth screens stand alone; everything else renders inside the
 * authenticated shell. Feature modules land as lazy children of the shell,
 * one route each behind the guard — dashboard and suppliers first.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/auth/verify-email.component').then((m) => m.VerifyEmailComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        data: { breadcrumb: 'Dashboard' },
      },
      {
        path: 'workspace',
        loadComponent: () =>
          import('./features/workspace/my-workspace.component').then((m) => m.MyWorkspaceComponent),
        data: { breadcrumb: 'My workspace' },
      },
      {
        path: 'suppliers',
        loadChildren: () =>
          import('./features/suppliers/suppliers.routes').then((m) => m.SUPPLIER_ROUTES),
      },
      {
        path: 'requests',
        loadChildren: () =>
          import('./features/procurement/procurement.routes').then((m) => m.PROCUREMENT_ROUTES),
      },
      {
        path: 'approvals',
        loadComponent: () =>
          import('./features/procurement/approval-inbox/approval-inbox.component').then(
            (m) => m.ApprovalInboxComponent,
          ),
        data: { breadcrumb: 'Approvals' },
      },
      {
        path: 'orders',
        loadChildren: () => import('./features/orders/orders.routes').then((m) => m.ORDER_ROUTES),
      },
      {
        path: 'invoices',
        loadChildren: () =>
          import('./features/invoices/invoices.routes').then((m) => m.INVOICE_ROUTES),
      },
      {
        path: 'budgets',
        loadChildren: () =>
          import('./features/budgets/budgets.routes').then((m) => m.BUDGET_ROUTES),
      },
      {
        path: 'analytics',
        loadComponent: () =>
          import('./features/analytics/analytics.component').then((m) => m.AnalyticsComponent),
        data: { breadcrumb: 'Analytics' },
      },
      {
        path: 'copilot',
        loadComponent: () =>
          import('./features/copilot/copilot.component').then((m) => m.CopilotComponent),
        data: { breadcrumb: 'Copilot' },
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications.component').then(
            (m) => m.NotificationsComponent,
          ),
        data: { breadcrumb: 'Notifications' },
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
        data: { breadcrumb: 'Settings' },
      },
      {
        path: 'admin',
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
