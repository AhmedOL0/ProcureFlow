import { Component, computed, inject, signal } from '@angular/core';
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
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { SuppliersService } from '../../suppliers/suppliers.service';
import { forkJoin } from 'rxjs';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { Plus, Search, Truck } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { OrderService, PurchaseOrder } from '../orders.service';
import { CreateOrderDialogComponent, CreateOrderDialogResult } from '../dialogs/create-order.dialog';

const STATUSES = ['DRAFT', 'SENT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'] as const;

/**
 * Purchase-order directory: server status filter, client supplier search,
 * sort and paging. Creation starts from an approved request — approving
 * never creates the order by itself.
 */
@Component({
  selector: 'app-order-list',
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatChipsModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTableModule, MatSortModule, MatPaginatorModule, MatProgressSpinnerModule,
    LucideAngularModule, PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './order-list.component.html',
})
export class OrderListComponent {
  private readonly orders = inject(OrderService);
  private readonly suppliers = inject(SuppliersService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly icons = { plus: Plus, search: Search, truck: Truck };
  protected readonly formatMinor = formatMinor;
  protected readonly canWrite = this.auth.hasAuthority('order:write');

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly statusFilter = new FormControl<string | null>(null);
  readonly columns = ['supplier', 'status', 'total', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  private readonly rows = signal<PurchaseOrder[]>([]);
  private readonly supplierNames = signal(new Map<string, string>());
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly statuses = STATUSES;
  protected readonly supplierNameFor = computed(() => {
    const names = this.supplierNames();
    return (supplierId: string | null | undefined): string => {
      if (!supplierId) {
        return '—';
      }
      return names.get(supplierId) ?? '…';
    };
  });

  protected readonly filtered = computed(() => {
    const query = this.searchBox.value.trim().toLowerCase();
    const names = this.supplierNames();
    return this.rows().filter((row) => {
      if (!query) {
        return true;
      }
      const name = row.supplierId ? (names.get(row.supplierId) ?? '') : '';
      return name.toLowerCase().includes(query);
    });
  });

  protected readonly pageRows = computed(() => {
    const sorted = [...this.filtered()].sort(compareOrders(this.sort(), this.supplierNames()));
    const { pageIndex, pageSize } = this.page();
    return sorted.slice(pageIndex * pageSize, pageIndex * pageSize + pageSize);
  });

  constructor() {
    this.reload();
    this.searchBox.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
    });
    this.statusFilter.valueChanges.subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
      this.reload();
    });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.orders.list(this.statusFilter.value).subscribe({
      next: (rows) => {
        this.rows.set(rows ?? []);
        this.page.update((p) => ({ ...p, pageIndex: 0 }));
        this.loading.set(false);
        this.resolveSupplierNames(rows ?? []);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  private resolveSupplierNames(rows: PurchaseOrder[]): void {
    const ids = [...new Set(rows.map((r) => r.supplierId).filter((id): id is string => !!id))];
    if (ids.length === 0) {
      this.supplierNames.set(new Map());
      return;
    }
    forkJoin(ids.map((id) => this.suppliers.get(id))).subscribe({
      next: (suppliers) => {
        const names = new Map<string, string>();
        for (const supplier of suppliers) {
          if (supplier.id) {
            names.set(supplier.id, supplier.name ?? '?');
          }
        }
        this.supplierNames.set(names);
      },
      error: () => undefined,
    });
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
    const dialog = this.dialogs.open<CreateOrderDialogComponent, void, CreateOrderDialogResult>(
      CreateOrderDialogComponent,
      { width: '520px' },
    );
    dialog.afterClosed().subscribe((result) => {
      if (result?.id) {
        void this.router.navigate(['/orders', result.id]);
      } else {
        this.reload();
      }
    });
  }
}

function compareOrders(sort: Sort, names: Map<string, string>): (a: PurchaseOrder, b: PurchaseOrder) => number {
  const direction = sort.direction === 'desc' ? -1 : 1;
  return (a, b) => {
    if (!sort.active || !sort.direction) {
      return 0;
    }
    if (sort.active === 'total') {
      return ((a.totalMinor ?? 0) - (b.totalMinor ?? 0)) * direction;
    }
    if (sort.active === 'supplier') {
      const left = a.supplierId ? (names.get(a.supplierId) ?? '') : '';
      const right = b.supplierId ? (names.get(b.supplierId) ?? '') : '';
      return left.localeCompare(right) * direction;
    }
    const left = ((a[sort.active as keyof PurchaseOrder] ?? '') as string).toString().toLowerCase();
    const right = ((b[sort.active as keyof PurchaseOrder] ?? '') as string).toString().toLowerCase();
    return left.localeCompare(right) * direction;
  };
}
