import { Routes } from '@angular/router';

/** Workspace administration: settings, departments, users, lanes, audit. */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./workspace.component').then((m) => m.WorkspaceComponent),
    data: { breadcrumb: 'Workspace' },
  },
  {
    path: 'departments',
    loadComponent: () => import('./departments.component').then((m) => m.DepartmentsComponent),
    data: { breadcrumb: 'Departments' },
  },
  {
    path: 'users',
    loadComponent: () => import('./users.component').then((m) => m.UsersComponent),
    data: { breadcrumb: 'Users' },
  },
  {
    path: 'workflows',
    loadComponent: () => import('./workflows.component').then((m) => m.WorkflowsComponent),
    data: { breadcrumb: 'Policies' },
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./audit-trail/audit-trail.component').then((m) => m.AuditTrailComponent),
    data: { breadcrumb: 'Audit trail' },
  },
];
