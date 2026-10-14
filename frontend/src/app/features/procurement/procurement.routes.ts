import { Routes } from '@angular/router';

import { pendingChangesGuard } from '../../core/guards/pending-changes.guard';

/** Purchase requests: directory, creation and dossiers, lazy under the shell. */
export const PROCUREMENT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./request-list/request-list.component').then((m) => m.RequestListComponent),
    data: { breadcrumbKey: 'req.crumbAll' },
  },
  {
    path: 'mine',
    loadComponent: () =>
      import('./request-list/request-list.component').then((m) => m.RequestListComponent),
    data: { breadcrumbKey: 'req.mineTitle', mode: 'mine' },
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./request-create/request-create.component').then((m) => m.RequestCreateComponent),
    canDeactivate: [pendingChangesGuard],
    data: { breadcrumbKey: 'req.newTitle' },
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./request-detail/request-detail.component').then((m) => m.RequestDetailComponent),
    data: { breadcrumbKey: 'req.fileCrumb' },
  },
];
