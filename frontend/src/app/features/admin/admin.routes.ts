import { Routes } from '@angular/router';

/** Workspace administration and audit visibility, lazy under the shell. */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin.component').then((m) => m.AdminComponent),
    data: { breadcrumb: 'Admin' },
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./audit-trail/audit-trail.component').then((m) => m.AuditTrailComponent),
    data: { breadcrumb: 'Audit trail' },
  },
];
