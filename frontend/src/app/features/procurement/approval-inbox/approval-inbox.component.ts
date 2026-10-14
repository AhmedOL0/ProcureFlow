import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, forkJoin, map, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { Inbox } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { timeAgo } from '../../../shared/utils/time';
import { Decision, ProcurementService, PurchaseRequest } from '../procurement.service';

interface DecidedRow {
  request: PurchaseRequest;
  decision: Decision | null;
}

/**
 * Approval inbox: submitted requests awaiting a verdict, plus recently
 * decided ones with their outcomes. Anyone with request access sees the
 * queue; deciding stays on the dossier, where assignment and delegation
 * are adjudicated by the backend (403s surface with a message).
 * Already-decided rows never offer actions — stale verdicts are impossible.
 * Both sections read bounded first pages (pending 100, decided 25+25) with
 * server totals, so the queue stays complete without unbounded reads.
 */
@Component({
  selector: 'app-approval-inbox',
  imports: [
    RouterLink, MatButtonModule, MatTableModule, MatProgressSpinnerModule, LucideAngularModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './approval-inbox.component.html',
  styleUrl: './approval-inbox.component.scss',
})
export class ApprovalInboxComponent {
  private readonly requests = inject(ProcurementService);
  private readonly auth = inject(AuthService);
  protected readonly lang = inject(LanguageService);

  protected readonly crumbs = computed(() => [{ label: this.lang.t('nav.approvals') }]);

  protected readonly icons = { inbox: Inbox };
  protected readonly formatMinor = formatMinor;
  protected readonly timeAgo = timeAgo;
  protected readonly canSee = this.auth.hasAuthority('procurement:approve')
    || this.auth.hasAuthority('procurement:request');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly pending = signal<PurchaseRequest[]>([]);
  protected readonly pendingTotal = signal(0);
  protected readonly decided = signal<DecidedRow[]>([]);
  protected readonly expanded = signal<string | null>(null);
  protected readonly details = signal(new Map<string, PurchaseRequest>());
  protected readonly columns = ['expand', 'title', 'priority', 'total', 'submitted', 'actions'];
  protected readonly decidedColumns = ['title', 'verdict', 'decidedAt', 'actions'];

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.requests.list('SUBMITTED', '', 0, 100).subscribe({
      next: (result) => {
        this.pending.set(result.rows);
        this.pendingTotal.set(result.total);
        this.loadDecided();
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  private loadDecided(): void {
    forkJoin([
      this.requests.list('APPROVED', '', 0, 25),
      this.requests.list('REJECTED', '', 0, 25),
    ]).subscribe({
      next: ([approved, rejected]) => {
        const rows = [...approved.rows, ...rejected.rows];
        if (rows.length === 0) {
          this.decided.set([]);
          this.loading.set(false);
          return;
        }
        forkJoin(
          rows.map((request) =>
            this.requests.decisionFor(request.id ?? '').pipe(
              map((decision) => ({ request, decision })),
              catchError(() => of({ request, decision: null })),
            ),
          ),
        ).subscribe({
          next: (decided) => {
            this.decided.set(decided);
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

  protected decidedAt(row: DecidedRow): string {    const at = row.decision?.createdAt;
    if (!at) {
      return 'N/A';
    }
    const date = new Date(at);
    return Number.isNaN(date.getTime()) ? 'N/A' : date.toLocaleString(this.lang.dateLocale());
  }

  protected submittedAt(request: PurchaseRequest): string {
    if (!request.submittedAt) {
      return 'N/A';
    }
    const date = new Date(request.submittedAt);
    return Number.isNaN(date.getTime()) ? 'N/A' : date.toLocaleString(this.lang.dateLocale());
  }

  protected expandLabel(row: PurchaseRequest): string {
    const title = row.title ?? '';
    return this.isExpanded(row)
      ? this.lang.tx('inbox.collapseTitle', { title })
      : this.lang.tx('inbox.expandTitle', { title });
  }

  toggle(row: PurchaseRequest): void {
    const id = row.id ?? null;
    const next = this.expanded() === id ? null : id;
    this.expanded.set(next);
    if (next && !this.details().has(next)) {
      this.requests.get(next).subscribe({
        next: (full) =>
          this.details.update((cache) => {
            const copy = new Map(cache);
            copy.set(next, full);
            return copy;
          }),
        error: () => undefined,
      });
    }
  }

  protected readonly whenExpanded = (_index: number, row: PurchaseRequest): boolean => this.isExpanded(row);

  protected isExpanded(row: PurchaseRequest): boolean {
    return !!row.id && this.expanded() === row.id;
  }

  protected expandedItems(row: PurchaseRequest): PurchaseRequest['items'] {
    if (!row.id) {
      return [];
    }
    return this.details().get(row.id)?.items ?? [];
  }
}
