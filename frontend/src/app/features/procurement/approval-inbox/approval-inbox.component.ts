import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, forkJoin, map, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { Inbox } from '../../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
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

  protected readonly icons = { inbox: Inbox };
  protected readonly formatMinor = formatMinor;
  protected readonly canSee = this.auth.hasAuthority('procurement:approve')
    || this.auth.hasAuthority('procurement:request');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly pending = signal<PurchaseRequest[]>([]);
  protected readonly decided = signal<DecidedRow[]>([]);
  protected readonly columns = ['title', 'priority', 'total', 'submitted', 'actions'];
  protected readonly decidedColumns = ['title', 'verdict', 'decidedAt', 'actions'];

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.requests.list('SUBMITTED').subscribe({
      next: (rows) => {
        this.pending.set(rows ?? []);
        this.loadDecided();
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  private loadDecided(): void {
    forkJoin([this.requests.list('APPROVED'), this.requests.list('REJECTED')]).subscribe({
      next: ([approved, rejected]) => {
        const rows = [...(approved ?? []), ...(rejected ?? [])];
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

  protected decidedAt(row: DecidedRow): string {
    const at = row.decision?.createdAt;
    if (!at) {
      return '—';
    }
    const date = new Date(at);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  protected submittedAt(request: PurchaseRequest): string {
    if (!request.submittedAt) {
      return '—';
    }
    const date = new Date(request.submittedAt);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }
}
