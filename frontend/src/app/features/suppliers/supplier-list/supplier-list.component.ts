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
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { Plus, Search, Truck } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { enrichSuppliers, initialsFor, SupplierRow } from '../supplier-rows';
import { Supplier, SupplierCategory, SuppliersService } from '../suppliers.service';
import {
  SupplierFormDialogComponent,
  SupplierFormData,
  SupplierFormResult,
} from '../dialogs/supplier-form.dialog';

interface Kpi {
  label: string;
  value: string;
  sub: string;
  bar: number | null;
}

/**
 * Supplier directory after the Stitch reference: KPI summary row, filter
 * console (search, status, category, quick chips) and a dense enriched
 * table. Every number is real — counts, scorecard averages; the reference's
 * spend/PO/compliance/region columns have no backing API and stay out.
 * Pagination and sorting stay client-side: the contract returns the full
 * workspace list.
 */
@Component({
  selector: 'app-supplier-list',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './supplier-list.component.html',
  styleUrl: './supplier-list.component.scss',
})
export class SupplierListComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);

  protected readonly icons = { plus: Plus, search: Search, truck: Truck, initials: initialsFor };
  protected readonly canWrite = this.auth.hasAuthority('supplier:write');

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly statusFilter = new FormControl<string | null>(null);
  readonly categoryFilter = new FormControl<string | null>(null);
  readonly columns = ['supplier', 'categories', 'contact', 'performance', 'status', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  readonly catalog = signal<SupplierCategory[]>([]);
  private readonly rows = signal<SupplierRow[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly filtered = computed(() => {
    const category = this.categoryFilter.value;
    return this.rows().filter((row) => !category || row.categories.some((c) => c.id === category));
  });

  protected readonly counts = computed(() => {
    const all = this.rows();
    return {
      all: all.length,
      active: all.filter((r) => r.supplier.status === 'ACTIVE').length,
      inactive: all.filter((r) => r.supplier.status === 'INACTIVE').length,
      suspended: all.filter((r) => r.supplier.status === 'SUSPENDED').length,
    };
  });

  protected readonly kpis = computed<Kpi[]>(() => {
    const all = this.rows();
    const scored = all.map((r) => r.averageOnTime).filter((v): v is number => v !== null);
    const avg = scored.length > 0 ? scored.reduce((a, b) => a + b, 0) / scored.length : null;
    const withScores = all.filter((r) => r.averageOnTime !== null).length;
    return [
      {
        label: 'Total suppliers',
        value: `${all.length}`,
        sub: `${this.counts().active} active`,
        bar: all.length > 0 ? (this.counts().active / all.length) * 100 : null,
      },
      {
        label: 'Avg on-time delivery',
        value: avg === null ? '—' : `${avg.toFixed(1)}%`,
        sub: `${withScores} with scorecards`,
        bar: avg,
      },
      {
        label: 'Suspended',
        value: `${this.counts().suspended}`,
        sub: this.counts().suspended === 1 ? 'needs review' : 'need review',
        bar: null,
      },
      {
        label: 'Categories in use',
        value: `${this.catalog().length}`,
        sub: 'workspace catalog',
        bar: null,
      },
    ];
  });

  protected readonly pageRows = computed(() => {
    const sorted = [...this.filtered()].sort(compareRows(this.sort()));
    const { pageIndex, pageSize } = this.page();
    return sorted.slice(pageIndex * pageSize, pageIndex * pageSize + pageSize);
  });

  constructor() {
    this.reload();
    this.searchBox.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.reload());
    this.statusFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reload());
    // Category filters client-side: nudge paging so the computed rows re-run.
    this.categoryFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.page.update((p) => ({ ...p, length: this.filtered().length, pageIndex: 0 }));
    });
    this.suppliers.catalog().subscribe({ next: (rows) => this.catalog.set(rows ?? []) });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.suppliers.search(this.statusFilter.value, this.searchBox.value).subscribe({
      next: (suppliers) => {
        enrichSuppliers(this.suppliers, suppliers ?? []).subscribe({
          next: (rows) => {
            this.rows.set(rows);
            this.page.update((p) => ({ ...p, length: this.filtered().length, pageIndex: 0 }));
            this.loading.set(false);
          },
          error: (error: unknown) => {
            this.failure.set(parseApiFailure(error));
            this.loading.set(false);
          },
        });
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  resetFilters(): void {
    this.searchBox.setValue('');
    this.statusFilter.setValue(null);
    this.categoryFilter.setValue(null);
  }

  setStatusFilter(status: string | null): void {
    this.statusFilter.setValue(status);
  }

  onSort(sort: Sort): void {
    this.sort.set(sort);
  }

  onPage(page: PageEvent): void {
    this.page.set(page);
  }

  openCreate(): void {
    const dialog = this.dialogs.open<
      SupplierFormDialogComponent,
      SupplierFormData,
      SupplierFormResult
    >(SupplierFormDialogComponent, { width: '480px', data: { mode: 'create' } });
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }
}

function rowText(row: SupplierRow, key: string): string {
  switch (key) {
    case 'supplier':
      return row.supplier.name ?? '';
    case 'status':
      return row.supplier.status ?? '';
    case 'email':
      return row.primaryContact?.email ?? '';
    case 'categories':
      return row.categories.map((c) => c.name ?? '').join(' ');
    case 'contact':
      return row.primaryContact?.name ?? '';
    case 'performance':
      return row.averageOnTime === null ? '' : `${row.averageOnTime}`;
    default:
      return '';
  }
}

function compareRows(sort: Sort): (a: SupplierRow, b: SupplierRow) => number {
  const direction = sort.direction === 'desc' ? -1 : 1;
  return (a, b) => {
    if (!sort.active || !sort.direction) {
      return 0;
    }
    const numeric = sort.active === 'performance';
    if (numeric) {
      const left = a.averageOnTime ?? Number.NEGATIVE_INFINITY;
      const right = b.averageOnTime ?? Number.NEGATIVE_INFINITY;
      return (left - right) * direction;
    }
    return rowText(a, sort.active).localeCompare(rowText(b, sort.active)) * direction;
  };
}

export type { Supplier };
