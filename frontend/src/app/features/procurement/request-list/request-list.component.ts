import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
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
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { FileText, Plus, Search } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { ProcurementService, PurchaseRequest } from '../procurement.service';

const STATUSES = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'ORDERED', 'CANCELLED'] as const;

/**
 * Purchase-request directory: server status filter, client title search,
 * sort and paging (the contract filters by status only). Rows open dossiers
 * where the request → approval → order story continues.
 */
@Component({
  selector: 'app-request-list',
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatChipsModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTableModule, MatSortModule, MatPaginatorModule, MatProgressSpinnerModule,
    LucideAngularModule, PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './request-list.component.html',
  styleUrl: './request-list.component.scss',
})
export class RequestListComponent {
  private readonly requests = inject(ProcurementService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly icons = { plus: Plus, search: Search, file: FileText };
  protected readonly formatMinor = formatMinor;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly statusFilter = new FormControl<string | null>(null);
  readonly columns = ['title', 'status', 'priority', 'total', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  private readonly rows = signal<PurchaseRequest[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly statuses = STATUSES;

  protected readonly filtered = computed(() => {
    const query = this.searchBox.value.trim().toLowerCase();
    return this.rows().filter(
      (row) => !query || (row.title ?? '').toLowerCase().includes(query),
    );
  });

  protected readonly counts = computed(() => {
    const counts = new Map<string, number>();
    for (const row of this.rows()) {
      counts.set(row.status ?? '?', (counts.get(row.status ?? '?') ?? 0) + 1);
    }
    return counts;
  });

  protected readonly pageRows = computed(() => {
    const sorted = [...this.filtered()].sort(compareRequests(this.sort()));
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
    this.requests.list(this.statusFilter.value).subscribe({
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

  goNew(): void {
    void this.router.navigate(['/requests/new']);
  }

  onSort(sort: Sort): void {
    this.sort.set(sort);
  }

  onPage(page: PageEvent): void {
    this.page.set(page);
  }
}

function compareRequests(sort: Sort): (a: PurchaseRequest, b: PurchaseRequest) => number {
  const direction = sort.direction === 'desc' ? -1 : 1;
  return (a, b) => {
    if (!sort.active || !sort.direction) {
      return 0;
    }
    if (sort.active === 'total') {
      return ((a.totalMinor ?? 0) - (b.totalMinor ?? 0)) * direction;
    }
    const left = ((a[sort.active as keyof PurchaseRequest] ?? '') as string).toString().toLowerCase();
    const right = ((b[sort.active as keyof PurchaseRequest] ?? '') as string).toString().toLowerCase();
    return left.localeCompare(right) * direction;
  };
}
