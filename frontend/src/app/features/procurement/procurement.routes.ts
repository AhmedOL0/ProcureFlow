import { Routes } from '@angular/router';

/** Purchase requests: directory, creation and dossiers, lazy under the shell. */
export const PROCUREMENT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./request-list/request-list.component').then((m) => m.RequestListComponent),
    data: { breadcrumb: 'Requests' },
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./request-wizard/request-wizard.component').then((m) => m.RequestWizardComponent),
    data: { breadcrumb: 'New request' },
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./request-detail/request-detail.component').then((m) => m.RequestDetailComponent),
    data: { breadcrumb: 'Request file' },
  },
];
