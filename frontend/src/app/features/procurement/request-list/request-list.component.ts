import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { FileText, Plus, Search } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { runDownload } from '../../../shared/utils/download';
import { formatDate } from '../../../shared/utils/format';
import { formatMinor } from '../../../shared/utils/money';
import {
  Department,
  Membership,
  OrganizationService,
  WorkspaceUser,
} from '../../admin/organization.service';
import { ProcurementService, PurchaseRequest } from '../procurement.service';

const STATUSES = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'ORDERED', 'CANCELLED'] as const;

/**
 * Purchase-request directory: status, title search and paging all run
 * server-side. Route data `mode: 'mine'` hits /purchase-requests/mine,
 * which forces the requester from the session — no caller id crosses the
 * wire. Requester name and department come from the workspace directory
 * (users + memberships + departments, first pages, failure-tolerant):
 * the request contract only carries requesterId. Client sorting orders
 * the loaded page only. Rows open dossiers where the request →
 * approval → order story continues. A request names no supplier — the
 * supplier is chosen when the approved request becomes an order, so the
 * supplier link lives on order rows instead.
 */
@Component({
  selector: 'app-request-list',
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
  templateUrl: './request-list.component.html',
  styleUrl: './request-list.component.scss',
})
export class RequestListComponent {
  private readonly requests = inject(ProcurementService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly org = inject(OrganizationService);

  protected readonly icons = { plus: Plus, search: Search, file: FileText };
  protected readonly formatMinor = formatMinor;
  protected readonly formatDate = formatDate;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');
  /** 'mine' shows only requests raised by the caller; anything else shows all. */
  protected readonly mineOnly = this.route.snapshot.data['mode'] === 'mine';

  readonly searchBox = new FormControl('', { nonNullable: true });
  readonly statusFilter = new FormControl<string | null>(null);
  readonly columns = ['title', 'requester', 'department', 'submitted', 'status', 'priority', 'total', 'actions'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  protected readonly busyExport = signal(false);
  protected readonly exportError = signal<string | null>(null);
  private readonly rows = signal<PurchaseRequest[]>([]);
  private readonly users = signal<WorkspaceUser[]>([]);
  private readonly memberships = signal<Membership[]>([]);
  private readonly departments = signal<Department[]>([]);
  private readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  protected readonly statuses = STATUSES;

  protected readonly pageRows = computed(() => [...this.rows()].sort(this.compareRows(this.sort())));

  constructor() {
    this.reload();
    this.loadDirectory();
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
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    const { pageIndex, pageSize } = this.page();
    const query = this.mineOnly
      ? this.requests.mine(this.statusFilter.value, this.searchBox.value, pageIndex, pageSize)
      : this.requests.list(this.statusFilter.value, this.searchBox.value, pageIndex, pageSize);
    query.subscribe({
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

  /** Workspace requests as xlsx; the /mine slice keeps no export. */
  exportXlsx(): void {
    runDownload(
      this.requests.exportXlsx(this.statusFilter.value, this.searchBox.value),
      this.busyExport,
      this.exportError,
      (error: unknown) => userMessageFor(parseApiFailure(error)),
    );
  }

  /**
   * Workspace directory for requester attribution. Loaded once, first
   * pages only; non-admin viewers typically get 403 here and simply see
   * dashes — the request list itself still loads.
   */
  private loadDirectory(): void {
    forkJoin({
      users: this.org.users(null, 0, 100).pipe(catchError(() => of({ rows: [], total: 0 }))),
      memberships: this.org.memberships(0, 100).pipe(catchError(() => of({ rows: [], total: 0 }))),
      departments: this.org.departments(null, 0, 100).pipe(catchError(() => of({ rows: [], total: 0 }))),
    }).subscribe((result) => {
      this.users.set(result.users.rows);
      this.memberships.set(result.memberships.rows);
      this.departments.set(result.departments.rows);
    });
  }

  protected requesterName(request: PurchaseRequest): string {
    const user = this.users().find((u) => u.id === request.requesterId);
    if (!user) {
      return 'N/A';
    }
    const full = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    return full || user.email || 'N/A';
  }

  protected requesterDepartments(request: PurchaseRequest): string {
    if (!request.requesterId) {
      return 'N/A';
    }
    const names = this.memberships()
      .filter((m) => m.userId === request.requesterId)
      .map((m) => this.departments().find((d) => d.id === m.departmentId)?.name ?? '')
      .filter(Boolean);
    return names.length > 0 ? names.join(', ') : 'N/A';
  }

  private compareRows(sort: Sort): (a: PurchaseRequest, b: PurchaseRequest) => number {
    const direction = sort.direction === 'desc' ? -1 : 1;
    return (a, b) => {
      if (!sort.active || !sort.direction) {
        return 0;
      }
      if (sort.active === 'total') {
        return ((a.totalMinor ?? 0) - (b.totalMinor ?? 0)) * direction;
      }
      if (sort.active === 'submitted') {
        return (a.submittedAt ?? a.createdAt ?? '').localeCompare(b.submittedAt ?? b.createdAt ?? '') * direction;
      }
      if (sort.active === 'requester') {
        return this.requesterName(a).toLowerCase().localeCompare(this.requesterName(b).toLowerCase()) * direction;
      }
      if (sort.active === 'department') {
        return this.requesterDepartments(a).toLowerCase().localeCompare(this.requesterDepartments(b).toLowerCase()) * direction;
      }
      const left = ((a[sort.active as keyof PurchaseRequest] ?? '') as string)
        .toString()
        .toLowerCase();
      const right = ((b[sort.active as keyof PurchaseRequest] ?? '') as string)
        .toString()
        .toLowerCase();
      return left.localeCompare(right) * direction;
    };
  }
}
