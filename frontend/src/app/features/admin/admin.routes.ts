import { Routes } from '@angular/router';

/** Workspace administration: settings, departments, users, lanes, audit. */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./workspace.component').then((m) => m.WorkspaceComponent),
    data: { breadcrumbKey: 'nav.adminWorkspace' },
  },
  {
    path: 'departments',
    loadComponent: () => import('./departments.component').then((m) => m.DepartmentsComponent),
    data: { breadcrumbKey: 'nav.departments' },
  },
  {
    path: 'users',
    loadComponent: () => import('./users.component').then((m) => m.UsersComponent),
    data: { breadcrumbKey: 'nav.users' },
  },
  {
    path: 'workflows',
    loadComponent: () => import('./workflows.component').then((m) => m.WorkflowsComponent),
    data: { breadcrumbKey: 'admin.policiesCrumb' },
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./audit-trail/audit-trail.component').then((m) => m.AuditTrailComponent),
    data: { breadcrumbKey: 'admin.trailTitle' },
  },
];
