import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ScrollText } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { runDownload } from '../../../shared/utils/download';
import { AuditEvent, AuditService } from '../organization.service';
import { LucideAngularModule } from 'lucide-angular';

/**
 * Every action the backend emits (verified at the AuditTrailLogged call
 * sites) and every entity type under audit. Chips set the same server
 * search as the text box — nothing here is a guess.
 */
const AUDIT_ACTIONS = [
  'BUDGET_CREATED',
  'BUDGET_DELETED',
  'INVOICE_CREATED',
  'INVOICE_PAID',
  'ORDER_CREATED',
  'ORDER_SENT',
  'ORDER_RECEIVED',
  'ORDER_CLOSED',
  'ORDER_CANCELLED',
  'REQUEST_DECIDED',
  'USER_STATUS_CHANGED',
  'USER_ROLES_CHANGED',
  'SUPPLIER_IMPORTED',
  'BUDGET_IMPORTED',
  'DOCUMENT_EXPORTED',
] as const;

const AUDIT_TYPES = ['budget', 'invoice', 'purchase_order', 'purchase_request', 'user'] as const;

/**
 * Read-only audit trail. Rows show actor, action, target and timestamps;
 * before/after payloads expand per row. Payloads carry statuses, ids and
 * comments only — never secrets. No mutation endpoints exist.
 */
@Component({
  selector: 'app-audit-trail',
  imports: [
    ReactiveFormsModule, MatButtonModule, MatChipsModule, MatFormFieldModule, MatInputModule, MatPaginatorModule,
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
  protected readonly auditActions = AUDIT_ACTIONS;
  protected readonly auditTypes = AUDIT_TYPES;

  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);
  protected readonly busyExport = signal(false);
  protected readonly exportError = signal<string | null>(null);
  protected readonly rows = signal<AuditEvent[]>([]);
  protected readonly page = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });
  protected readonly expanded = signal<string | null>(null);

  /**
   * Free-text search over action and entity type, executed server-side so
   * matches count every page instead of the loaded one.
   */
  constructor() {
    this.reload();
    this.entityFilter.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page.update((p) => ({ ...p, pageIndex: 0 }));
        this.reload();
      });
  }

  reload(): void {
    const current = this.page();
    this.load(current.pageIndex, current.pageSize);
  }

  load(pageIndex: number, pageSize: number): void {
    this.loading.set(true);
    this.failure.set(null);
    this.trail.page(null, null, this.entityFilter.value, pageIndex, pageSize).subscribe({
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

  /** Chips toggle the server search; clicking the active chip clears it. */
  pickQuery(term: string): void {
    this.entityFilter.setValue(this.entityFilter.value === term ? '' : term);
  }

  /** Filtered trail as xlsx (who/what/when; payloads stay in the UI). */
  exportXlsx(): void {
    runDownload(
      this.trail.exportXlsx(this.entityFilter.value),
      this.busyExport,
      this.exportError,
      (error: unknown) => userMessageFor(parseApiFailure(error)),
    );
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
      return 'N/A';
    }
    const date = new Date(at);
    return Number.isNaN(date.getTime()) ? 'N/A' : date.toLocaleString();
  }

  protected shortId(id: string | null | undefined): string {
    return id ? `${id.slice(0, 8)}...` : 'N/A';
  }
}
