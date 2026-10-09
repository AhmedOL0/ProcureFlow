import { Routes } from '@angular/router';

/** Supplier directory and dossiers, lazy under the shell. */
export const SUPPLIER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./supplier-list/supplier-list.component').then((m) => m.SupplierListComponent),
    data: { breadcrumb: 'Suppliers' },
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./supplier-detail/supplier-detail.component').then((m) => m.SupplierDetailComponent),
    data: { breadcrumb: 'Supplier file' },
  },
];
