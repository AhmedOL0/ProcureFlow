import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { computed } from '@angular/core';

import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ScrollText } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { AuditEvent, AuditService } from '../organization.service';
import { LucideAngularModule } from 'lucide-angular';

/**
 * Read-only audit trail. Rows show actor, action, target and timestamps;
 * before/after payloads expand per row. Payloads carry statuses, ids and
 * comments only — never secrets. No mutation endpoints exist.
 */
@Component({
  selector: 'app-audit-trail',
  imports: [
    ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatPaginatorModule,
    MatProgressSpinnerModule, MatTableModule, LucideAngularModule, RouterLink,
    PageHeaderComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './audit-trail.component.html',
  styleUrl: './audit-trail.component.scss',
})
export class AuditTrailComponent {
  private readonly trail = inject(AuditService);

  protected readonly icons = { scroll: ScrollText };
  readonly entityFilter = new FormControl('', { nonNullable: true });
  readonly columns = ['time', 'actor', 'action', 'target'];

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  private readonly rows = signal<AuditEvent[]>([]);
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });
  protected readonly expanded = signal<string | null>(null);

  /**
   * Free-text filter over the loaded page. Server-side search across all
   * pages awaits a search param on the contract; until then the filter
   * narrows what the paginator fetched, and the counts stay server-true.
   */
  protected readonly filtered = computed(() => {
    const query = this.entityFilter.value.trim().toLowerCase();
    const rows = this.rows();
    if (!query) {
      return rows;
    }
    return rows.filter(
      (row) =>
        (row.action ?? '').toLowerCase().includes(query) ||
        (row.entityType ?? '').toLowerCase().includes(query) ||
        (row.entityId ?? '').toLowerCase().includes(query),
    );
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    const current = this.page();
    this.load(current.pageIndex, current.pageSize);
  }

  load(pageIndex: number, pageSize: number): void {
    this.loading.set(true);
    this.failure.set(null);
    this.trail.page(null, null, pageIndex, pageSize).subscribe({
      next: (result) => {
        this.rows.set(result.rows);
        this.page.set({ pageIndex, pageSize, length: result.total });
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  onPage(page: PageEvent): void {
    this.load(page.pageIndex, page.pageSize);
  }

  toggle(row: AuditEvent): void {
    this.expanded.update((current) => (current === row.id ? null : (row.id ?? null)));
  }

  protected readonly whenExpanded = (_index: number, row: AuditEvent): boolean => this.isExpanded(row);

  protected isExpanded(row: AuditEvent): boolean {
    return !!row.id && this.expanded() === row.id;
  }

  protected readableTime(at: string | null | undefined): string {
    if (!at) {
      return '—';
    }
    const date = new Date(at);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  protected shortId(id: string | null | undefined): string {
    return id ? `${id.slice(0, 8)}…` : '—';
  }
}
