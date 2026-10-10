import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, forkJoin, of } from 'rxjs';

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
  Receipt,
  Truck,
  Wallet,
} from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatMinor } from '../../shared/utils/money';
import { AnalyticsService, ApprovalKpi, SpendMonth, SpendResponse } from '../analytics/analytics.service';
import { Budget, BudgetsService } from '../budgets/budgets.service';
import { Invoice, InvoiceService } from '../invoices/invoices.service';
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
 * Procurement overview in per-authority sections. The operations pulse
 * (requests, approvals, suppliers, orders) loads for every member; the
 * financial pulse (spend, outstanding, budget) and the approval-flow strip
 * load only when the caller holds the matching authority — a missing grant
 * hides its section instead of failing the page. Every figure names its
 * source; outstanding sums the complete workspace invoice list (the
 * contract returns it unpaged), never a sampled page.
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
  private readonly invoices = inject(InvoiceService);
  private readonly budgets = inject(BudgetsService);
  private readonly analytics = inject(AnalyticsService);
  private readonly auth = inject(AuthService);

  protected readonly icons = {
    plus: Plus, open: FileText, approvals: Inbox, suppliers: Truck, orders: ClipboardList,
    money: Receipt, wallet: Wallet,
  };
  protected readonly formatMinor = formatMinor;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');
  private readonly canSeeFinance = this.auth.hasAuthority('analytics:read');
  private readonly canSeeBudgets = this.auth.hasAuthority('budget:read');
  private readonly canSeeInvoices = this.auth.hasAuthority('invoice:read');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  private readonly allRequests = signal<PurchaseRequest[]>([]);
  private readonly submitted = signal<PurchaseRequest[]>([]);
  private readonly activeSuppliers = signal<number | null>(null);
  private readonly openOrders = signal<number | null>(null);
  protected readonly spend = signal<SpendResponse | null>(null);
  protected readonly approvalKpis = signal<ApprovalKpi | null>(null);
  private readonly monthPots = signal<Budget[]>([]);
  protected readonly monthPotMissing = signal(false);
  private readonly invoiceRows = signal<Invoice[]>([]);
  protected readonly financeFailed = signal(false);

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

  protected readonly showFinance = computed(
    () => this.canSeeFinance && (this.spend() !== null || this.approvalKpis() !== null),
  );

  protected readonly financeKpis = computed<Kpi[]>(() => {
    const flow = this.spend();
    return [
      {
        label: 'Ordered spend',
        value: flow ? formatMinor(flow.orderedMinor) : '—',
        sub: 'committed to suppliers',
        icon: Receipt,
      },
      {
        label: 'Paid out',
        value: flow ? formatMinor(flow.paidMinor) : '—',
        sub: 'settled with suppliers',
        icon: Receipt,
      },
      {
        label: 'Outstanding invoices',
        value: this.outstandingLabel().value,
        sub: this.outstandingLabel().sub,
        icon: Receipt,
      },
      {
        label: 'Budget left (month)',
        value: this.budgetLabel().value,
        sub: this.budgetLabel().sub,
        icon: Wallet,
      },
    ];
  });

  private outstandingLabel(): { value: string; sub: string } {
    if (!this.canSeeInvoices) {
      return { value: '—', sub: 'no invoice access' };
    }
    const open = this.invoiceRows().filter((row) => row.status === 'UNPAID' || row.status === 'PARTIAL');
    if (open.length === 0) {
      return { value: formatMinor(0), sub: 'nothing outstanding' };
    }
    const currencies = new Set(open.map((row) => row.currency ?? 'MAD'));
    if (currencies.size !== 1) {
      return { value: `${open.length} open`, sub: 'mixed currencies' };
    }
    const due = open.reduce((acc, row) => acc + (row.totalMinor ?? 0) - (row.paidMinor ?? 0), 0);
    return { value: formatMinor(due, [...currencies][0]), sub: `${open.length} awaiting payment` };
  }

  private budgetLabel(): { value: string; sub: string } {
    if (!this.canSeeBudgets) {
      return { value: '—', sub: 'no budget access' };
    }
    const pots = this.monthPots();
    if (pots.length === 0) {
      return { value: 'No pot', sub: this.monthPotMissing() ? 'no pot this month' : 'loading' };
    }
    const currencies = new Set(pots.map((pot) => pot.currency ?? 'MAD'));
    if (currencies.size !== 1) {
      return { value: `${pots.length} pots`, sub: 'mixed currencies' };
    }
    const currency = [...currencies][0];
    const remaining = pots.reduce((acc, pot) => acc + (pot.remainingMinor ?? 0), 0);
    const allocated = pots.reduce((acc, pot) => acc + (pot.amountMinor ?? 0), 0);
    return {
      value: formatMinor(remaining, currency),
      sub: `of ${formatMinor(allocated, currency)} across ${pots.length === 1 ? '1 pot' : `${pots.length} pots`}`,
    };
  }

  protected readonly trend = computed<SpendMonth[]>(() => (this.spend()?.byPeriod ?? []).slice(-6));

  protected readonly trendMax = computed(() =>
    this.trend().reduce((max, row) => Math.max(max, row.orderedMinor ?? 0), 0),
  );

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
    this.financeFailed.set(false);
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
        this.loadFinance();
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  private loadFinance(): void {
    const period = this.budgets.currentPeriodUtc();
    forkJoin({
      spend: this.canSeeFinance ? this.analytics.spend(period) : of(null),
      approvals: this.canSeeFinance ? this.analytics.approvalKpis() : of(null),
      pots: this.canSeeBudgets ? this.budgets.list(period) : of(null),
      invoices: this.canSeeInvoices ? this.invoices.list(null) : of(null),
    })
      .pipe(
        catchError(() => {
          this.financeFailed.set(true);
          return of({ spend: null, approvals: null, pots: null, invoices: null });
        }),
      )
      .subscribe({
        next: ({ spend, approvals, pots, invoices }) => {
          this.spend.set(spend);
          this.approvalKpis.set(approvals);
          const rows = pots ?? [];
          this.monthPots.set(rows);
          this.monthPotMissing.set(this.canSeeBudgets && rows.length === 0);
          this.invoiceRows.set(invoices ?? []);
        },
      });
  }

  trendWidth(value: number | null | undefined): number {
    const max = this.trendMax();
    if (value === null || value === undefined || !Number.isFinite(value) || max <= 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, (value / max) * 100));
  }

  leadHours(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return '—';
    }
    return `${value.toFixed(1)}h`;
  }
}
