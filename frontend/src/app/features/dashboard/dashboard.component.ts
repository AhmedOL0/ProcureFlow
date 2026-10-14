import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SpendChartComponent, SpendPoint } from '../../shared/components/spend-chart/spend-chart.component';
import { DonutChartComponent, DonutSlice } from '../../shared/components/donut-chart/donut-chart.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import {
  Building2,
  ChartColumn,
  CircleCheck,
  FileClock,
  FileText,
  Inbox,
  Plus,
  Receipt,
  ShieldCheck,
  Stamp,
  Truck,
  Wallet,
} from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatDecimal } from '../../shared/utils/format';
import { formatMinor } from '../../shared/utils/money';
import { initialsFor } from '../../shared/utils/names';
import { timeAgo } from '../../shared/utils/time';
import {
  AnalyticsService,
  ApprovalKpi,
  SpendMonth,
  SpendResponse,
} from '../analytics/analytics.service';
import { toSpendCategories } from '../analytics/spend-categories';
import { Budget, BudgetsService } from '../budgets/budgets.service';
import { OrderService } from '../orders/orders.service';
import { ProcurementService, PurchaseRequest } from '../procurement/procurement.service';
import { SuppliersService } from '../suppliers/suppliers.service';

interface Kpi {
  label: string;
  value: string;
  sub: string;
  icon: typeof Truck;
}

export type TrendMode = 'monthly' | 'category';

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
    MatChipsModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SpendChartComponent,
    DonutChartComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly requests = inject(ProcurementService);
  private readonly router = inject(Router);
  private readonly suppliers = inject(SuppliersService);
  private readonly orders = inject(OrderService);
  private readonly budgets = inject(BudgetsService);
  private readonly analytics = inject(AnalyticsService);
  private readonly auth = inject(AuthService);
  protected readonly lang = inject(LanguageService);

  protected readonly icons = {
    plus: Plus,
    open: FileText,
    approvals: Inbox,
    shield: ShieldCheck,
    initials: initialsFor,
  };
  protected readonly formatMinor = formatMinor;
  protected readonly timeAgo = timeAgo;
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
    const lang = this.lang;
    return [
      {
        label: lang.t('dashboard.openRequests'),
        value: `${open}`,
        sub: lang.tx('dashboard.awaitingSub', { n: this.submittedTotal() }),
        icon: FileClock,
      },
      {
        label: lang.t('dashboard.pendingApprovals'),
        value: `${this.submittedTotal()}`,
        sub: open === 0 ? lang.t('dashboard.queueClear') : lang.t('dashboard.inQueue'),
        icon: Stamp,
      },
      {
        label: lang.t('dashboard.activeSuppliers'),
        value: this.activeSuppliers() === null ? 'N/A' : `${this.activeSuppliers()}`,
        sub: lang.t('dashboard.vendorsSub'),
        icon: Building2,
      },
      {
        label: lang.t('dashboard.openOrders'),
        value: this.openOrders() === null ? 'N/A' : `${this.openOrders()}`,
        sub: lang.t('dashboard.sentSub'),
        icon: Truck,
      },
    ];
  });

  protected readonly showFinance = computed(
    () => this.canSeeFinance && (this.spend() !== null || this.approvalKpis() !== null),
  );

  protected readonly financeKpis = computed<Kpi[]>(() => {
    const flow = this.spend();
    const lang = this.lang;
    const orderedDelta = this.monthDelta((month) => month.orderedMinor ?? 0);
    const paidDelta = this.monthDelta((month) => month.paidMinor ?? 0);
    return [
      {
        label: lang.t('dashboard.orderedSpend'),
        value: this.mixedSpend() ? this.spendCountLabel() : flow ? formatMinor(flow.orderedMinor, this.spendCode()) : 'N/A',
        sub: this.mixedSpend() ? lang.t('dashboard.splitAnalytics') : (orderedDelta ?? lang.t('dashboard.committedSub')),
        icon: ChartColumn,
      },
      {
        label: lang.t('dashboard.paidOut'),
        value: this.mixedSpend() ? this.spendCountLabel() : flow ? formatMinor(flow.paidMinor, this.spendCode()) : 'N/A',
        sub: this.mixedSpend() ? lang.t('dashboard.splitAnalytics') : (paidDelta ?? lang.t('dashboard.settledSub')),
        icon: CircleCheck,
      },
      {
        label: lang.t('dashboard.outstanding'),
        value: this.outstandingLabel().value,
        sub: this.outstandingLabel().sub,
        icon: Receipt,
      },
      {
        label: lang.t('dashboard.budgetLeft'),
        value: this.budgetLabel().value,
        sub: this.budgetLabel().sub,
        icon: Wallet,
      },
    ];
  });

  /** Distinct ISO codes behind the spend aggregates; more than one means mixed. */
  protected readonly mixedSpend = computed(() => (this.spend()?.currencies ?? []).length > 1);

  /** Codes behind the aggregates, for the mixed notice and KPI labels. */
  protected readonly spendCodes = computed<string[]>(() => this.spend()?.currencies ?? []);

  /** Display code for single-currency aggregates; legacy responses default MAD. */
  /** Display code for single-currency aggregates; legacy responses default MAD. */
  protected spendCode(): string {
    const codes = this.spendCodes();
    return codes.length === 1 ? (codes[0] ?? 'MAD') : 'MAD';
  }

  protected spendCountLabel(): string {
    const n = this.spendCodes().length;
    return n <= 1 ? this.lang.t('dashboard.oneCurrency') : this.lang.tx('dashboard.currencyCount', { n });
  }

  /**
   * Month-over-month delta from the spend aggregates (complete monthly
   * buckets, never sampled rows). Absent with fewer than two months — or
   * with mixed currencies, where a merged percentage would lie — and the
   * KPI falls back to its plain subtitle instead of inventing one.
   */
  private monthDelta(pick: (month: SpendMonth) => number): string | null {
    if (this.mixedSpend()) {
      return null;
    }
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
    const period = months[months.length - 2]?.period ?? 'prior';
    return this.lang.tx('dashboard.deltaVs', { pct: `${sign}${formatDecimal(pct)}`, period });
  }

  private outstandingLabel(): { value: string; sub: string } {
    // Outstanding = invoiced minus paid from the server aggregates — no
    // invoice-list scan, bounded at any scale. Meaningless across codes, so
    // mixed tenants get a pointer instead of a merged figure.
    if (!this.canSeeInvoices) {
      return { value: 'N/A', sub: this.lang.t('dashboard.noInvoiceAccess') };
    }
    const flow = this.spend();
    if (!flow) {
      return { value: 'N/A', sub: this.lang.t('dashboard.figuresUnavailable') };
    }
    if (this.mixedSpend()) {
      return { value: this.lang.t('dashboard.mixedSpend'), sub: this.lang.t('dashboard.splitAnalytics') };
    }
    const due = (flow.invoicedMinor ?? 0) - (flow.paidMinor ?? 0);
    if (due <= 0) {
      return { value: formatMinor(0, this.spendCode()), sub: this.lang.t('dashboard.nothingOutstanding') };
    }
    return { value: formatMinor(due, this.spendCode()), sub: this.lang.t('dashboard.awaitingPayment') };
  }

  private budgetLabel(): { value: string; sub: string } {
    if (!this.canSeeBudgets) {
      return { value: 'N/A', sub: this.lang.t('dashboard.noBudgetAccess') };
    }
    const pots = this.monthPots();
    if (pots.length === 0) {
      return {
        value: this.lang.t('dashboard.noPot'),
        sub: this.monthPotMissing() ? this.lang.t('dashboard.noPotMonth') : this.lang.t('dashboard.loadingData'),
      };
    }
    const currencies = new Set(pots.map((pot) => pot.currency ?? 'MAD'));
    if (currencies.size !== 1) {
      return { value: `${pots.length} pots`, sub: this.lang.t('dashboard.mixedCurrencies') };
    }
    const currency = [...currencies][0];
    const remaining = pots.reduce((acc, pot) => acc + (pot.remainingMinor ?? 0), 0);
    const allocated = pots.reduce((acc, pot) => acc + (pot.amountMinor ?? 0), 0);
    const count = pots.length === 1 ? this.lang.t('dashboard.onePot') : this.lang.tx('dashboard.potsSub', { n: pots.length });
    return {
      value: formatMinor(remaining, currency),
      sub: this.lang.tx('dashboard.capSub', { n: count, amount: formatMinor(allocated, currency) }),
    };
  }

  protected readonly trend = computed<SpendMonth[]>(() => (this.spend()?.byPeriod ?? []).slice(-6));

  protected readonly trendMode = signal<TrendMode>('monthly');

  protected readonly trendPoints = computed<SpendPoint[]>(() =>
    this.trend().map((month) => ({
      period: month.period ?? '',
      ordered: month.orderedMinor ?? 0,
      invoiced: month.invoicedMinor ?? 0,
      paid: month.paidMinor ?? 0,
    })),
  );

  protected readonly trendSlices = computed<DonutSlice[]>(() =>
    toSpendCategories(this.spend()?.byCategory).map((row) => ({
      label: row.category,
      value: row.amountMinor,
    })),
  );

  protected readonly decisionRate = computed<string | null>(() => {
    const flow = this.approvalKpis();
    if (!flow) {
      return null;
    }
    const total = (flow.pending ?? 0) + (flow.decided ?? 0);
    if (total <= 0) {
      return null;
    }
    return this.lang.tx('dashboard.decisionRatePct', { pct: Math.round(((flow.decided ?? 0) / total) * 100) });
  });

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

  /** Chart drill: a trend month opens the full trajectory at that month. */
  openAnalytics(period: string): void {
    void this.router.navigate(['/analytics'], { queryParams: period ? { period } : {} });
  }

  /** Priority chip tone: urgency reads danger, routine reads neutral. */
  priorityTone(priority: string | null | undefined): string {
    switch (priority) {
      case 'HIGH':
      case 'URGENT':
        return 'pf-badge--danger';
      case 'MEDIUM':
        return 'pf-badge--warning';
      default:
        return 'pf-badge--neutral';
    }
  }

  leadHours(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return 'N/A';
    }
    return this.lang.tx('dashboard.leadHours', { n: formatDecimal(value) });
  }
}
