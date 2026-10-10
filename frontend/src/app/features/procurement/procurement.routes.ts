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
    path: 'mine',
    loadComponent: () =>
      import('./request-list/request-list.component').then((m) => m.RequestListComponent),
    data: { breadcrumb: 'My requests', mode: 'mine' },
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./request-create/request-create.component').then((m) => m.RequestCreateComponent),
    data: { breadcrumb: 'New request' },
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./request-detail/request-detail.component').then((m) => m.RequestDetailComponent),
    data: { breadcrumb: 'Request file' },
  },
];
