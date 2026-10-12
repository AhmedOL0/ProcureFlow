import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime, distinctUntilChanged, forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DonutChartComponent } from '../../shared/components/donut-chart/donut-chart.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SpendChartComponent, SpendPoint } from '../../shared/components/spend-chart/spend-chart.component';
import { Inbox, Truck, Wallet } from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatMinor } from '../../shared/utils/money';
import {
  AiUsage,
  AnalyticsService,
  ApprovalKpi,
  SpendMonth,
  SpendResponse,
  SupplierKpi,
  toSpendCategories,
} from './analytics.service';

/**
 * Analytics & reports: spend trajectory, category allocation, supplier
 * scorecards and approval lead times — all server-computed aggregates,
 * rendered as CSS bars (no chart dependency). The AI panel shows metered
 * usage only: the copilot actions answer 503 while AI_ENABLED=false, so
 * no chat UI ships until the backend enables them.
 */
@Component({
  selector: 'app-analytics',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SpendChartComponent,
    DonutChartComponent,
  ],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss',
})
export class AnalyticsComponent {
  private readonly analytics = inject(AnalyticsService);
  private readonly auth = inject(AuthService);

  protected readonly icons = { wallet: Wallet, truck: Truck, inbox: Inbox };
  protected readonly formatMinor = formatMinor;
  protected readonly canSeeAiUsage = this.auth.hasAuthority('ai:use');

  readonly periodBox = new FormControl('', { nonNullable: true });
  readonly categoryBox = new FormControl('', { nonNullable: true });
  readonly supplierBox = new FormControl('', { nonNullable: true });

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly spend = signal<SpendResponse | null>(null);
  protected readonly supplierKpis = signal<SupplierKpi[]>([]);
  protected readonly approvalKpis = signal<ApprovalKpi | null>(null);
  protected readonly aiUsage = signal<AiUsage | null>(null);
  private readonly categoryQuery = signal('');
  private readonly supplierQuery = signal('');

  /**
   * Server-side filtering stops at the period: the contracts expose no
   * department/supplier/status/category parameters, so the two searches
   * below filter the returned aggregates client-side and say so.
   */
  protected readonly categories = computed(() => {
    const query = this.categoryQuery().trim().toLowerCase();
    return toSpendCategories(this.spend()?.byCategory).filter(
      (row) => !query || row.category.toLowerCase().includes(query),
    );
  });
  protected readonly suppliers = computed(() => {
    const query = this.supplierQuery().trim().toLowerCase();
    return this.supplierKpis().filter(
      (row) => !query || (row.supplierName ?? '').toLowerCase().includes(query),
    );
  });
  protected readonly months = computed<SpendMonth[]>(() => this.spend()?.byPeriod ?? []);

  protected readonly spendPoints = computed<SpendPoint[]>(() =>
    this.months().map((month) => ({
      period: month.period ?? '',
      ordered: month.orderedMinor ?? 0,
      invoiced: month.invoicedMinor ?? 0,
      paid: month.paidMinor ?? 0,
    })),
  );

  protected readonly donutSlices = computed(() =>
    this.categories().map((row) => ({ label: row.category, value: row.amountMinor })),
  );

  protected readonly supplierColumns = ['supplier', 'periods', 'onTime', 'quality'];

  /**
   * Recent-month shortcuts over the single-month filter. Quarters would
   * need a backend range aggregate; these set honest single months.
   */
  protected readonly recentMonths = computed<string[]>(() => {
    const cursor = new Date();
    cursor.setUTCDate(1);
    return [0, 1, 2].map((back) => {
      const date = new Date(Date.UTC(cursor.getUTCFullYear(), cursor.getUTCMonth() - back, 1));
      return date.toISOString().slice(0, 7);
    });
  });

  constructor() {
    this.periodBox.setValue(this.analytics.currentPeriodUtc());
    this.categoryBox.valueChanges
      .pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.categoryQuery.set(value));
    this.supplierBox.valueChanges
      .pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.supplierQuery.set(value));
    this.reload();
  }

  reload(): void {
    const period = this.periodBox.value.trim() === '' ? null : this.periodBox.value.trim();
    this.loading.set(true);
    this.failure.set(null);
    forkJoin({
      spend: this.analytics.spend(period),
      suppliers: this.analytics.supplierKpis(),
      approvals: this.analytics.approvalKpis(),
      // The usage meter needs ai:use; without it the row is absent rather
      // than failing the whole page.
      usage: this.canSeeAiUsage ? this.analytics.aiUsage(period) : of(null),
    }).subscribe({
      next: ({ spend, suppliers, approvals, usage }) => {
        this.spend.set(spend);
        this.supplierKpis.set(suppliers ?? []);
        this.approvalKpis.set(approvals);
        this.aiUsage.set(usage);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  clearPeriod(): void {
    this.periodBox.setValue('');
    this.reload();
  }

  pickMonth(period: string): void {
    if (this.periodBox.value !== period) {
      this.periodBox.setValue(period);
      this.reload();
    }
  }

  /**
   * CSV of exactly what the page shows (loaded aggregates, not a server
   * export — there is no export endpoint). Built client-side and
   * downloaded via Blob; fields are quoted so commas in names survive.
   */
  exportCsv(): void {
    const period = this.periodBox.value.trim() === '' ? 'all-time' : this.periodBox.value.trim();
    const lines = ['section,period,name,ordered_minor,invoiced_minor,paid_minor'];
    for (const month of this.months()) {
      lines.push([
        'month',
        month.period ?? '',
        '',
        month.orderedMinor ?? 0,
        month.invoicedMinor ?? 0,
        month.paidMinor ?? 0,
      ].map(csvCell).join(','));
    }
    for (const row of this.categories()) {
      lines.push(['category', period, row.category, row.amountMinor, '', ''].map(csvCell).join(','));
    }
    for (const row of this.suppliers()) {
      lines.push([
        'supplier',
        '',
        row.supplierName ?? '',
        row.periods ?? 0,
        row.avgOnTime ?? '',
        row.avgQuality ?? '',
      ].map(csvCell).join(','));
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `procureflow-analytics-${period}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  /** Chart drill: a month bar narrows the whole page to that period. */
  drillToMonth(period: string): void {
    if (!period || this.periodBox.value === period) {
      return;
    }
    this.periodBox.setValue(period);
    this.reload();
  }

  /** Chart drill: a donut segment fills the category search. */
  drillToCategory(category: string): void {
    if (!category) {
      return;
    }
    this.categoryBox.setValue(category);
  }

  barWidth(value: number, max: number): number {    if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, (value / max) * 100));
  }

  hours(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return '—';
    }
    return `${value.toFixed(1)}h`;
  }
}

function csvCell(value: string | number): string {
  const text = `${value ?? ''}`;
  return `"${text.replace(/"/g, '""')}"`;
}
