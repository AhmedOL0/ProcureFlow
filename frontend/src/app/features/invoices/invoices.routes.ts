import { Routes } from '@angular/router';

/** Invoices: directory and payment dossiers, lazy under the shell. */
export const INVOICE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./invoice-list/invoice-list.component').then((m) => m.InvoiceListComponent),
    data: { breadcrumbKey: 'nav.invoices' },
  },
  {
    path: ':id',
    loadComponent: () => import('./invoice-detail/invoice-detail.component').then((m) => m.InvoiceDetailComponent),
    data: { breadcrumbKey: 'inv.crumbFile' },
  },
];
