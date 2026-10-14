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
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { Plus, Search, Truck } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { runDownload } from '../../../shared/utils/download';
import { enrichSuppliers, initialsFor, SupplierRow } from '../supplier-rows';
import { Supplier, SupplierCategory, SuppliersService } from '../suppliers.service';
import {
  SupplierFormDialogComponent,
  SupplierFormData,
  SupplierFormResult,
} from '../dialogs/supplier-form.dialog';
import {
  CreateOrderDialogComponent,
  CreateOrderDialogData,
  CreateOrderDialogResult,
} from '../../orders/dialogs/create-order.dialog';
import { SupplierImportDialogComponent } from '../dialogs/supplier-import.dialog';

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
  private readonly router = inject(Router);

  protected readonly icons = { plus: Plus, search: Search, truck: Truck, initials: initialsFor };
  protected readonly canWrite = this.auth.hasAuthority('supplier:write');
  protected readonly canOrder = this.auth.hasAuthority('order:write');

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly statusFilter = new FormControl<string | null>(null);
  readonly categoryFilter = new FormControl<string | null>(null);
  readonly columns = ['supplier', 'categories', 'contact', 'performance', 'status', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  protected readonly busyExport = signal(false);
  protected readonly exportError = signal<string | null>(null);
  readonly catalog = signal<SupplierCategory[]>([]);
  private readonly rows = signal<SupplierRow[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly filtered = computed(() => {
    const category = this.categoryFilter.value;
    return this.rows().filter((row) => !category || row.categories.some((c) => c.id === category));
  });

  /**
   * Status totals come from size-1 server reads so chips and KPIs stay exact
   * at any scale; the on-time average covers the loaded page (scorecards
   * would need a full scan otherwise) and says so.
   */
  protected readonly totals = signal({ all: 0, active: 0, inactive: 0, suspended: 0 });

  protected readonly kpis = computed<Kpi[]>(() => {
    const rows = this.rows();
    const scored = rows.map((r) => r.averageOnTime).filter((v): v is number => v !== null);
    const avg = scored.length > 0 ? scored.reduce((a, b) => a + b, 0) / scored.length : null;
    const totals = this.totals();
    return [
      {
        label: 'Total suppliers',
        value: `${totals.all}`,
        sub: `${totals.active} active`,
        bar: totals.all > 0 ? (totals.active / totals.all) * 100 : null,
      },
      {
        label: 'Avg on-time delivery',
        value: avg === null ? 'N/A' : `${avg.toFixed(1)}%`,
        sub: 'across this page',
        bar: avg,
      },
      {
        label: 'Suspended',
        value: `${totals.suspended}`,
        sub: totals.suspended === 1 ? 'needs review' : 'need review',
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

  protected readonly pageRows = computed(() => [...this.filtered()].sort(compareRows(this.sort())));

  constructor() {
    this.reload();
    this.searchBox.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page.update((p) => ({ ...p, pageIndex: 0 }));
        this.reload();
      });
    this.statusFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
      this.reload();
    });
    this.suppliers.catalog().subscribe({ next: (rows) => this.catalog.set(rows ?? []) });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    const { pageIndex, pageSize } = this.page();
    const query = this.searchBox.value;
    this.suppliers.search(this.statusFilter.value, query, pageIndex, pageSize).subscribe({
      next: (result) => {
        enrichSuppliers(this.suppliers, result.rows).subscribe({
          next: (rows) => {
            this.rows.set(rows);
            this.page.update((p) => ({ ...p, length: result.total }));
            this.loading.set(false);
          },
          error: (error: unknown) => {
            this.failure.set(parseApiFailure(error));
            this.loading.set(false);
          },
        });
        this.refreshTotals(query);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  private refreshTotals(query: string): void {
    forkJoin({
      all: this.suppliers.search(null, query, 0, 1),
      active: this.suppliers.search('ACTIVE', query, 0, 1),
      inactive: this.suppliers.search('INACTIVE', query, 0, 1),
      suspended: this.suppliers.search('SUSPENDED', query, 0, 1),
    }).subscribe({
      next: (totals) => {
        this.totals.set({
          all: totals.all.total,
          active: totals.active.total,
          inactive: totals.inactive.total,
          suspended: totals.suspended.total,
        });
      },
      error: () => undefined,
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
    this.reload();
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

  /** Filtered directory as xlsx; failures show inline, never a dialog. */
  exportXlsx(): void {
    runDownload(
      this.suppliers.exportXlsx(this.statusFilter.value, this.searchBox.value),
      this.busyExport,
      this.exportError,
      (error: unknown) => userMessageFor(parseApiFailure(error)),
    );
  }

  openImport(): void {
    const dialog = this.dialogs.open<SupplierImportDialogComponent, undefined, boolean>(
      SupplierImportDialogComponent,
      { width: '520px' },
    );
    dialog.afterClosed().subscribe((imported) => {
      if (imported) {
        this.reload();
      }
    });
  }

  /**
   * Start an order with this supplier pre-selected. The dialog still
   * requires an approved request — a supplier row alone can never mint
   * an order. Success lands on the new order dossier.
   */
  openOrder(supplierId: string | null | undefined): void {
    if (!supplierId) {
      return;
    }
    const dialog = this.dialogs.open<
      CreateOrderDialogComponent,
      CreateOrderDialogData,
      CreateOrderDialogResult
    >(CreateOrderDialogComponent, { width: '520px', data: { supplierId } });
    dialog.afterClosed().subscribe((created) => {
      if (created?.id) {
        this.router.navigate(['/orders', created.id]);
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
