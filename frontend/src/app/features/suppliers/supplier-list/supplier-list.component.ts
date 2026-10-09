import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
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
import { Supplier, SuppliersService } from '../suppliers.service';
import { SupplierFormDialogComponent, SupplierFormData, SupplierFormResult } from '../dialogs/supplier-form.dialog';

/**
 * Supplier directory: server-filtered search plus status filter, client
 * sorting and paging (the contract returns the full workspace list —
 * pagination is deliberately client-side, see the code comment). Rows link
 * to dossiers; writes hide without supplier:write.
 */
@Component({
  selector: 'app-supplier-list',
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTableModule, MatSortModule, MatPaginatorModule, MatProgressSpinnerModule,
    LucideAngularModule, PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './supplier-list.component.html',
  styleUrl: './supplier-list.component.scss',
})
export class SupplierListComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);

  protected readonly icons = { plus: Plus, search: Search, truck: Truck };
  protected readonly canWrite = this.auth.hasAuthority('supplier:write');

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly statusFilter = new FormControl<string | null>(null);
  readonly columns = ['name', 'status', 'email', 'phone', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  private readonly rows = signal<Supplier[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly total = computed(() => this.rows().length);
  protected readonly pageRows = computed(() => {
    const sorted = [...this.rows()].sort(compareSuppliers(this.sort()));
    const { pageIndex, pageSize } = this.page();
    return sorted.slice(pageIndex * pageSize, pageIndex * pageSize + pageSize);
  });

  constructor() {
    this.reload();
    this.searchBox.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
      this.reload();
    });
    this.statusFilter.valueChanges.subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
      this.reload();
    });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.suppliers.search(this.statusFilter.value, this.searchBox.value).subscribe({
      next: (rows) => {
        this.rows.set(rows ?? []);
        this.page.update((p) => ({ ...p, length: (rows ?? []).length, pageIndex: 0 }));
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  onSort(sort: Sort): void {
    this.sort.set(sort);
  }

  onPage(page: PageEvent): void {
    this.page.set(page);
  }

  openCreate(): void {
    const dialog = this.dialogs.open<SupplierFormDialogComponent, SupplierFormData, SupplierFormResult>(
      SupplierFormDialogComponent,
      { width: '480px', data: { mode: 'create' } },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }
}

function compareSuppliers(sort: Sort): (a: Supplier, b: Supplier) => number {
  const direction = sort.direction === 'desc' ? -1 : 1;
  return (a, b) => {
    const left = (a[sort.active as keyof Supplier] ?? '').toString().toLowerCase();
    const right = (b[sort.active as keyof Supplier] ?? '').toString().toLowerCase();
    if (!sort.active || !sort.direction) {
      return 0;
    }
    return left.localeCompare(right) * direction;
  };
}
