import { Routes } from '@angular/router';

/** Purchase orders: directory and fulfillment dossiers, lazy under the shell. */
export const ORDER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./order-list/order-list.component').then((m) => m.OrderListComponent),
    data: { breadcrumb: 'Orders' },
  },
  {
    path: ':id',
    loadComponent: () => import('./order-detail/order-detail.component').then((m) => m.OrderDetailComponent),
    data: { breadcrumb: 'Order file' },
  },
];
