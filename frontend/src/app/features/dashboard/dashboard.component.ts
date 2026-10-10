import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import {
  ClipboardList,
  FileText,
  Inbox,
  Plus,
  Truck,
} from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatMinor } from '../../shared/utils/money';
import { OrderService } from '../orders/orders.service';
import { ProcurementService, PurchaseRequest } from '../procurement/procurement.service';
import { SuppliersService } from '../suppliers/suppliers.service';

interface Kpi {
  label: string;
  value: string;
  sub: string;
  icon: typeof Truck;
}

/**
 * Procurement overview: workspace KPIs, the approval queue and recent
 * requests — every number and row comes from a live contract call.
 * Nothing here is illustrative: a failed load renders an error state,
 * an empty workspace renders empty states, never zeros-as-content.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    RouterLink, MatButtonModule, MatProgressSpinnerModule, LucideAngularModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly requests = inject(ProcurementService);
  private readonly suppliers = inject(SuppliersService);
  private readonly orders = inject(OrderService);
  private readonly auth = inject(AuthService);

  protected readonly icons = {
    plus: Plus, open: FileText, approvals: Inbox, suppliers: Truck, orders: ClipboardList,
  };
  protected readonly formatMinor = formatMinor;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  private readonly allRequests = signal<PurchaseRequest[]>([]);
  private readonly submitted = signal<PurchaseRequest[]>([]);
  private readonly activeSuppliers = signal<number | null>(null);
  private readonly openOrders = signal<number | null>(null);

  protected readonly kpis = computed<Kpi[]>(() => {
    const open = this.allRequests().filter((r) => r.status === 'DRAFT' || r.status === 'SUBMITTED').length;
    return [
      {
        label: 'Open requests',
        value: `${open}`,
        sub: `${this.submitted().length} awaiting approval`,
        icon: FileText,
      },
      {
        label: 'Pending approvals',
        value: `${this.submitted().length}`,
        sub: open === 0 ? 'queue is clear' : 'in the approval queue',
        icon: Inbox,
      },
      {
        label: 'Active suppliers',
        value: this.activeSuppliers() === null ? '—' : `${this.activeSuppliers()}`,
        sub: 'vendors you can buy from',
        icon: Truck,
      },
      {
        label: 'Open orders',
        value: this.openOrders() === null ? '—' : `${this.openOrders()}`,
        sub: 'sent or partially received',
        icon: ClipboardList,
      },
    ];
  });

  protected readonly awaiting = computed(() => this.submitted().slice(0, 3));

  protected readonly recent = computed(() =>
    [...this.allRequests()]
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
      .slice(0, 5),
  );

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    forkJoin({
      all: this.requests.list(null),
      submitted: this.requests.list('SUBMITTED'),
      suppliers: this.suppliers.search('ACTIVE', null),
      orders: this.orders.list(null),
    }).subscribe({
      next: ({ all, submitted, suppliers: found, orders: orderRows }) => {
        this.allRequests.set(all ?? []);
        this.submitted.set(submitted ?? []);
        this.activeSuppliers.set((found ?? []).length);
        this.openOrders.set(
          (orderRows ?? []).filter((o) => o.status === 'SENT' || o.status === 'PARTIALLY_RECEIVED').length,
        );
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }
}
