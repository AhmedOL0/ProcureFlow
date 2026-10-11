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
import { FileText, Inbox, Plus, ShieldCheck } from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatMinor } from '../../shared/utils/money';
import {
  AnalyticsService,
  ApprovalKpi,
  SpendMonth,
  SpendResponse,
} from '../analytics/analytics.service';
import { Budget, BudgetsService } from '../budgets/budgets.service';
import { OrderService } from '../orders/orders.service';
import { ProcurementService, PurchaseRequest } from '../procurement/procurement.service';
import { SuppliersService } from '../suppliers/suppliers.service';

interface Kpi {
  label: string;
  value: string;
  sub: string;
}

/**
 * Procurement overview in per-authority sections. The operations pulse
 * (requests, approvals, suppliers, orders) loads for every member; the
 * financial pulse (spend, outstanding, budget) and the approval-flow strip
 * load only when the caller holds the matching authority — a missing grant
 * hides its section instead of failing the page. Counts come from server
 * totals and rows from small first pages; outstanding reads invoiced
 * minus paid off the spend aggregates, never a sampled page.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly requests = inject(ProcurementService);
  private readonly suppliers = inject(SuppliersService);
  private readonly orders = inject(OrderService);
  private readonly budgets = inject(BudgetsService);
  private readonly analytics = inject(AnalyticsService);
  private readonly auth = inject(AuthService);

  protected readonly icons = { plus: Plus, open: FileText, approvals: Inbox, shield: ShieldCheck };
  protected readonly formatMinor = formatMinor;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');
  protected readonly canSeeAudit = this.auth.hasAuthority('audit:read');
  private readonly canSeeFinance = this.auth.hasAuthority('analytics:read');
  private readonly canSeeBudgets = this.auth.hasAuthority('budget:read');
  private readonly canSeeInvoices = this.auth.hasAuthority('invoice:read');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  private readonly openTotal = signal(0);
  private readonly submittedTotal = signal(0);
  protected readonly awaiting = signal<PurchaseRequest[]>([]);
  protected readonly recent = signal<PurchaseRequest[]>([]);
  private readonly activeSuppliers = signal<number | null>(null);
  private readonly openOrders = signal<number | null>(null);
  protected readonly spend = signal<SpendResponse | null>(null);
  protected readonly approvalKpis = signal<ApprovalKpi | null>(null);
  private readonly monthPots = signal<Budget[]>([]);
  protected readonly monthPotMissing = signal(false);
  protected readonly financeFailed = signal(false);

  protected readonly kpis = computed<Kpi[]>(() => {
    const open = this.openTotal();
    return [
      {
        label: 'Open requests',
        value: `${open}`,
        sub: `${this.submittedTotal()} awaiting approval`,
      },
      {
        label: 'Pending approvals',
        value: `${this.submittedTotal()}`,
        sub: open === 0 ? 'queue is clear' : 'in the approval queue',
      },
      {
        label: 'Active suppliers',
        value: this.activeSuppliers() === null ? '—' : `${this.activeSuppliers()}`,
        sub: 'vendors you can buy from',
      },
      {
        label: 'Open orders',
        value: this.openOrders() === null ? '—' : `${this.openOrders()}`,
        sub: 'sent or partially received',
      },
    ];
  });

  protected readonly showFinance = computed(
    () => this.canSeeFinance && (this.spend() !== null || this.approvalKpis() !== null),
  );

  protected readonly financeKpis = computed<Kpi[]>(() => {
    const flow = this.spend();
    const orderedDelta = this.monthDelta((month) => month.orderedMinor ?? 0);
    const paidDelta = this.monthDelta((month) => month.paidMinor ?? 0);
    return [
      {
        label: 'Ordered spend (all time)',
        value: flow ? formatMinor(flow.orderedMinor) : '—',
        sub: orderedDelta ?? 'committed to suppliers',
      },
      {
        label: 'Paid out (all time)',
        value: flow ? formatMinor(flow.paidMinor) : '—',
        sub: paidDelta ?? 'settled with suppliers',
      },
      {
        label: 'Outstanding invoices',
        value: this.outstandingLabel().value,
        sub: this.outstandingLabel().sub,
      },
      {
        label: 'Budget left (month)',
        value: this.budgetLabel().value,
        sub: this.budgetLabel().sub,
      },
    ];
  });

  /**
   * Month-over-month delta from the spend aggregates (complete monthly
   * buckets, never sampled rows). Absent with fewer than two months —
   * the KPI falls back to its plain subtitle instead of inventing one.
   */
  private monthDelta(pick: (month: SpendMonth) => number): string | null {
    const months = [...(this.spend()?.byPeriod ?? [])]
      .filter((month) => typeof month.period === 'string')
      .sort((a, b) => (a.period as string).localeCompare(b.period as string));
    if (months.length < 2) {
      return null;
    }
    const current = pick(months[months.length - 1] as SpendMonth);
    const previous = pick(months[months.length - 2] as SpendMonth);
    if (!Number.isFinite(current) || !Number.isFinite(previous) || previous <= 0) {
      return null;
    }
    const pct = ((current - previous) / previous) * 100;
    const sign = pct > 0 ? '+' : '';
    return `${sign}${pct.toFixed(1)}% vs ${months[months.length - 2]?.period ?? 'prior'}`;
  }

  private outstandingLabel(): { value: string; sub: string } {
    // Outstanding = invoiced minus paid from the server aggregates (single
    // currency per workspace, as the contract documents) — no invoice-list
    // scan, bounded at any scale.
    if (!this.canSeeInvoices) {
      return { value: '—', sub: 'no invoice access' };
    }
    const flow = this.spend();
    if (!flow) {
      return { value: '—', sub: 'figures unavailable' };
    }
    const due = (flow.invoicedMinor ?? 0) - (flow.paidMinor ?? 0);
    if (due <= 0) {
      return { value: formatMinor(0), sub: 'nothing outstanding' };
    }
    return { value: formatMinor(due), sub: 'awaiting payment' };
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
    const count = pots.length === 1 ? '1 pot' : `${pots.length} pots`;
    return {
      value: formatMinor(remaining, currency),
      sub: `${count} · cap ${formatMinor(allocated, currency)}`,
    };
  }

  protected readonly trend = computed<SpendMonth[]>(() => (this.spend()?.byPeriod ?? []).slice(-6));

  protected readonly trendMax = computed(() =>
    this.trend().reduce((max, row) => Math.max(max, row.orderedMinor ?? 0), 0),
  );

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.financeFailed.set(false);
    // Counts come from server totals (size-1 reads), rows from small first
    // pages — no unbounded list is ever loaded for this overview.
    forkJoin({
      draft: this.requests.list('DRAFT', '', 0, 1),
      submitted: this.requests.list('SUBMITTED', '', 0, 3),
      recent: this.requests.list(null, '', 0, 5),
      suppliers: this.suppliers.search('ACTIVE', null, 0, 1),
      sent: this.orders.list('SENT', 0, 1),
      partial: this.orders.list('PARTIALLY_RECEIVED', 0, 1),
    }).subscribe({
      next: ({ draft, submitted, recent, suppliers: found, sent, partial }) => {
        this.openTotal.set(draft.total + submitted.total);
        this.submittedTotal.set(submitted.total);
        this.awaiting.set(submitted.rows);
        this.recent.set(recent.rows);
        this.activeSuppliers.set(found.total);
        this.openOrders.set(sent.total + partial.total);
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
      spend: this.canSeeFinance ? this.analytics.spend(null) : of(null),
      approvals: this.canSeeFinance ? this.analytics.approvalKpis() : of(null),
      pots: this.canSeeBudgets ? this.budgets.list(period, '', 0, 100) : of(null),
    })
      .pipe(
        catchError(() => {
          this.financeFailed.set(true);
          return of({ spend: null, approvals: null, pots: null });
        }),
      )
      .subscribe({
        next: ({ spend, approvals, pots }) => {
          this.spend.set(spend);
          this.approvalKpis.set(approvals);
          const rows = pots?.rows ?? [];
          this.monthPots.set(rows);
          this.monthPotMissing.set(this.canSeeBudgets && rows.length === 0);
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
