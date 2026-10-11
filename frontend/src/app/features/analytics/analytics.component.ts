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
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
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

  protected readonly categoryMax = computed(() =>
    this.categories().reduce((max, row) => Math.max(max, row.amountMinor), 0),
  );
  protected readonly monthMax = computed(() =>
    this.months().reduce(
      (max, row) =>
        Math.max(max, row.orderedMinor ?? 0, row.invoicedMinor ?? 0, row.paidMinor ?? 0),
      0,
    ),
  );

  protected readonly supplierColumns = ['supplier', 'periods', 'onTime', 'quality'];

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

  barWidth(value: number, max: number): number {
    if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) {
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
