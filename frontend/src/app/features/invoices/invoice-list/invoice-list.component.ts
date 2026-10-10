import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../../core/auth/auth.service';
import { Router } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { FileText, Plus } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { Invoice, InvoiceService } from '../invoices.service';
import { CreateInvoiceDialogComponent, CreateInvoiceDialogResult } from '../dialogs/create-invoice.dialog';

const STATUSES = ['UNPAID', 'PARTIAL', 'PAID'] as const;

/**
 * Invoice directory: server status filter, client number search, sort and
 * paging. Status is read, never edited — payments move it, and overpaying
 * is rejected. This screen tracks payment status; it never moves money.
 */
@Component({
  selector: 'app-invoice-list',
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatChipsModule, MatFormFieldModule,
    MatSelectModule, MatTableModule, MatSortModule, MatPaginatorModule, MatProgressSpinnerModule,
    LucideAngularModule, PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './invoice-list.component.html',
})
export class InvoiceListComponent {
  private readonly invoices = inject(InvoiceService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly icons = { file: FileText, plus: Plus };
  protected readonly formatMinor = formatMinor;
  protected readonly canWrite = this.auth.hasAuthority('invoice:write');

  readonly statusFilter = new FormControl<string | null>(null);
  readonly columns = ['number', 'status', 'total', 'paid', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  private readonly rows = signal<Invoice[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly total = computed(() => this.rows().length);

  protected readonly statuses = STATUSES;

  protected readonly pageRows = computed(() => {
    const sorted = [...this.rows()].sort(compareInvoices(this.sort()));
    const { pageIndex, pageSize } = this.page();
    return sorted.slice(pageIndex * pageSize, pageIndex * pageSize + pageSize);
  });

  constructor() {
    this.reload();
    this.statusFilter.valueChanges.subscribe(() => {
      this.page.update((p) => ({ ...p, pageIndex: 0 }));
      this.reload();
    });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.invoices.list(this.statusFilter.value).subscribe({
      next: (rows) => {
        this.rows.set(rows ?? []);
        this.page.update((p) => ({ ...p, pageIndex: 0 }));
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
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
    const dialog = this.dialogs.open<CreateInvoiceDialogComponent, void, CreateInvoiceDialogResult>(
      CreateInvoiceDialogComponent,
      { width: '560px' },
    );
    dialog.afterClosed().subscribe((result) => {
      if (result?.id) {
        void this.router.navigate(['/invoices', result.id]);
      } else if (result) {
        this.reload();
      }
    });
  }
}

function compareInvoices(sort: Sort): (a: Invoice, b: Invoice) => number {
  const direction = sort.direction === 'desc' ? -1 : 1;
  return (a, b) => {
    if (!sort.active || !sort.direction) {
      return 0;
    }
    if (sort.active === 'total' || sort.active === 'paid') {
      const key = sort.active === 'total' ? 'totalMinor' : 'paidMinor';
      return ((a[key] ?? 0) - (b[key] ?? 0)) * direction;
    }
    const left = ((a[sort.active as keyof Invoice] ?? '') as string).toString().toLowerCase();
    const right = ((b[sort.active as keyof Invoice] ?? '') as string).toString().toLowerCase();
    return left.localeCompare(right) * direction;
  };
}
