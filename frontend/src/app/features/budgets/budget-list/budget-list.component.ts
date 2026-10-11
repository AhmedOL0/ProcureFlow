import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { Plus, Search, Wallet } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { Budget, BudgetsService } from '../budgets.service';
import { BudgetFormDialogComponent } from '../dialogs/budget-form.dialog';

/**
 * Budgets & spend: pots with allocation, reservation and remainder, paged
 * server-side with period and name filters. Totals count pots exactly;
 * money sums cover the loaded page (labeled as such) — a workspace-wide
 * rollup would need a dedicated aggregate. Sorting orders the loaded page.
 * Money sums across pots only when the pots share one currency.
 */
@Component({
  selector: 'app-budget-list',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './budget-list.component.html',
  styleUrl: './budget-list.component.scss',
})
export class BudgetListComponent {
  private readonly budgets = inject(BudgetsService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);

  protected readonly icons = { plus: Plus, search: Search, wallet: Wallet };
  protected readonly formatMinor = formatMinor;
  protected readonly canManage = this.auth.hasAuthority('budget:manage');
  protected readonly periods = this.budgets.recentPeriods();

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly periodFilter = new FormControl<string | null>(null);
  readonly columns = ['pot', 'allocated', 'reserved', 'remaining', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  private readonly rows = signal<Budget[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly totals = computed(() => {
    const page = this.rows();
    const currencies = new Set(page.map((row) => row.currency ?? 'MAD'));
    const sum = (pick: (row: Budget) => number): number =>
      page.reduce((acc, row) => acc + (pick(row) ?? 0), 0);
    return {
      pots: this.page().length,
      committed: page.filter((row) => (row.remainingMinor ?? 0) <= 0).length,
      singleCurrency: currencies.size <= 1 ? [...currencies][0] ?? 'MAD' : null,
      allocated: sum((row) => row.amountMinor ?? 0),
      reserved: sum((row) => row.reservedMinor ?? 0),
    };
  });

  protected readonly pageRows = computed(() => [...this.rows()].sort(compareBudgets(this.sort())));

  constructor() {
    this.reload();
    this.searchBox.valueChanges
      .pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page.update((p) => ({ ...p, pageIndex: 0 }));
        this.reload();
      });
    this.periodFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
      this.reload();
    });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    const { pageIndex, pageSize } = this.page();
    this.budgets.list(this.periodFilter.value, this.searchBox.value, pageIndex, pageSize).subscribe({
      next: (result) => {
        this.rows.set(result.rows);
        this.page.update((p) => ({ ...p, length: result.total }));
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  resetFilters(): void {
    this.searchBox.setValue('');
    this.periodFilter.setValue(null);
  }

  setPeriod(period: string | null): void {
    this.periodFilter.setValue(period);
  }

  onSort(sort: Sort): void {
    this.sort.set(sort);
  }

  onPage(page: PageEvent): void {
    this.page.set(page);
    this.reload();
  }

  utilization(row: Budget): number {
    const amount = row.amountMinor ?? 0;
    if (amount <= 0) {
      return 0;
    }
    return Math.min(100, ((row.reservedMinor ?? 0) / amount) * 100);
  }

  openCreate(): void {
    const dialog = this.dialogs.open<BudgetFormDialogComponent, object, boolean>(
      BudgetFormDialogComponent,
      { width: '480px', data: {} },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }
}

function numeric(row: Budget, key: string): number {
  switch (key) {
    case 'allocated':
      return row.amountMinor ?? Number.NEGATIVE_INFINITY;
    case 'reserved':
      return row.reservedMinor ?? Number.NEGATIVE_INFINITY;
    case 'remaining':
      return row.remainingMinor ?? Number.NEGATIVE_INFINITY;
    default:
      return Number.NEGATIVE_INFINITY;
  }
}

function compareBudgets(sort: Sort): (a: Budget, b: Budget) => number {
  const direction = sort.direction === 'desc' ? -1 : 1;
  return (a, b) => {
    if (!sort.active || !sort.direction) {
      return 0;
    }
    if (sort.active === 'pot') {
      return (
        `${a.period ?? ''} ${a.name ?? ''}`.localeCompare(`${b.period ?? ''} ${b.name ?? ''}`) *
        direction
      );
    }
    return (numeric(a, sort.active) - numeric(b, sort.active)) * direction;
  };
}
