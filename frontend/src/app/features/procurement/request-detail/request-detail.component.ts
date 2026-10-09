import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, forkJoin, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Budget, BudgetContextService } from '../budget.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { FileText, Pencil, Plus, Trash2 } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { SuppliersService } from '../../suppliers/suppliers.service';
import {
  ApprovalState,
  Decision,
  ProcurementService,
  PurchaseRequest,
  RequestItem,
} from '../procurement.service';
import { DecideDialogComponent, DecideDialogData, DecideDialogResult } from '../dialogs/decide.dialog';
import { RequestItemDialogComponent, RequestItemDialogData, RequestItemDialogResult } from '../dialogs/request-item.dialog';
import { RequestFormDialogComponent, RequestFormData, RequestFormResult } from '../dialogs/request-form.dialog';

/**
 * Request file: the draft → submit → decide story on one screen. Items are
 * editable while DRAFT; submit needs at least one line; approve/reject is
 * offered to approvers and delegates (the backend adjudicates 403s with a
 * message). Approving reserves the monthly budget in the same transaction —
 * the decision card says so because the backend guarantees it.
 */
@Component({
  selector: 'app-request-detail',
  imports: [
    RouterLink, MatButtonModule, MatProgressSpinnerModule, MatSnackBarModule, LucideAngularModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './request-detail.component.html',
  styleUrl: './request-detail.component.scss',
})
export class RequestDetailComponent {
  private readonly requests = inject(ProcurementService);
  private readonly suppliers = inject(SuppliersService);
  private readonly budgets = inject(BudgetContextService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);
  private readonly snacks = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly icons = { file: FileText, pencil: Pencil, plus: Plus, trash: Trash2 };
  protected readonly formatMinor = formatMinor;

  protected readonly loading = signal(true);
  protected readonly acting = signal(false);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly request = signal<PurchaseRequest | null>(null);
  protected readonly decision = signal<Decision | null>(null);
  protected readonly lane = signal<ApprovalState | null>(null);
  protected readonly budget = signal<Budget | 'missing' | null>(null);
  protected readonly canSeeBudget = this.auth.hasAuthority('budget:read');
  protected readonly supplierNames = signal(new Map<string, string>());

  protected readonly isDraft = computed(() => this.request()?.status === 'DRAFT');
  protected readonly isSubmitted = computed(() => this.request()?.status === 'SUBMITTED');
  protected readonly canEdit = computed(() => {
    const request = this.request();
    const user = this.auth.currentUser();
    return (
      !!request &&
      request.status === 'DRAFT' &&
      (!!user && (user.id === request.requesterId || this.auth.hasAuthority('tenant:admin')))
    );
  });
  protected readonly canDecide = computed(
    () => this.auth.hasAuthority('procurement:approve') || this.auth.hasAuthority('procurement:request'),
  );
  protected readonly supplierNameFor = computed(() => {
    const names = this.supplierNames();
    return (supplierId: string | null | undefined): string => {
      if (!supplierId) {
        return '—';
      }
      return names.get(supplierId) ?? '…';
    };
  });

  private requestId(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  constructor() {
    this.reload();
  }

  reload(): void {
    const id = this.requestId();
    if (!id) {
      this.failure.set({ status: 404, code: null, message: 'This record does not exist in your workspace.' });
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.failure.set(null);
    this.requests.get(id).subscribe({
      next: (request) => {
        this.request.set(request);
        this.loading.set(false);
        this.loadDecision(id);
        this.loadLane(id);
        this.loadBudget();
        this.resolveSupplierNames(request.items ?? []);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }

  private loadDecision(id: string): void {
    this.decision.set(null);
    this.requests.decisionFor(id).subscribe({
      next: (decision) => this.decision.set(decision),
      error: () => undefined,
    });
  }

  private loadLane(id: string): void {
    this.lane.set(null);
    this.requests.approvalState(id).subscribe({
      next: (state) => this.lane.set(state),
      error: () => undefined,
    });
  }

  /** Current-month pot for the budget card; 'missing' means no governance configured. */
  private loadBudget(): void {
    this.budget.set(null);
    if (!this.canSeeBudget) {
      return;
    }
    this.budgets.pots(this.budgets.currentPeriodUtc()).subscribe({
      next: (pots) => this.budget.set(pots.length > 0 && pots[0] ? pots[0] : 'missing'),
      error: () => this.budget.set('missing'),
    });
  }

  protected historyEntries(): { label: string; at: string | null | undefined }[] {
    const request = this.request();
    const verdict = this.decision();
    return [
      { label: 'Created', at: request?.createdAt },
      { label: 'Submitted', at: request?.submittedAt },
      { label: 'Decided', at: verdict?.createdAt },
    ];
  }

  protected readableDate(at: string | null | undefined): string {
    if (!at) {
      return '—';
    }
    const date = new Date(at);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  private announce(message: string): void {
    this.snacks.open(message, 'Dismiss', { duration: 4000 });
  }

  private resolveSupplierNames(items: RequestItem[]): void {
    const ids = [...new Set(items.map((i) => i.supplierId).filter((id): id is string => !!id))];
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

  openEdit(): void {
    const current = this.request();
    if (!current) {
      return;
    }
    const dialog = this.dialogs.open<RequestFormDialogComponent, RequestFormData, RequestFormResult>(
      RequestFormDialogComponent,
      { width: '480px', data: { mode: 'edit', request: current } },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  confirmDelete(): void {
    const current = this.request();
    if (!current?.id) {
      return;
    }
    const dialog = this.dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: `Delete “${current.title}”?`,
        message: 'Draft requests delete with their lines. This cannot be undone.',
        confirmLabel: 'Delete request',
      },
    });
    dialog.afterClosed().subscribe((confirmed) => {
      if (confirmed === true && current.id) {
        this.requests.remove(current.id).subscribe({
          next: () => void this.router.navigate(['/requests']),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }

  submit(): void {
    const current = this.request();
    if (!current?.id || this.acting()) {
      return;
    }
    this.acting.set(true);
    this.requests.submit(current.id).subscribe({
      next: () => {
        this.acting.set(false);
        this.announce('Request submitted for approval.');
        this.reload();
      },
      error: (error: unknown) => {
        this.acting.set(false);
        this.failure.set(parseApiFailure(error));
      },
    });
  }

  cancel(): void {
    const current = this.request();
    if (!current?.id || this.acting()) {
      return;
    }
    this.acting.set(true);
    this.requests.cancel(current.id).subscribe({
      next: () => {
        this.acting.set(false);
        this.announce('Request cancelled.');
        this.reload();
      },
      error: (error: unknown) => {
        this.acting.set(false);
        this.failure.set(parseApiFailure(error));
      },
    });
  }

  decide(approved: boolean): void {
    const current = this.request();
    if (!current?.id) {
      return;
    }
    const dialog = this.dialogs.open<DecideDialogComponent, DecideDialogData, DecideDialogResult>(
      DecideDialogComponent,
      { width: '440px', data: { approved, title: current.title ?? '' } },
    );
    dialog.afterClosed().subscribe((result) => {
      if (!result || !current.id || this.acting()) {
        return;
      }
      this.acting.set(true);
      this.requests.decide(current.id, approved, result.comment).subscribe({
        next: () => {
          this.acting.set(false);
          this.announce(approved ? 'Approved — budget reserved.' : 'Rejected.');
          this.reload();
        },
        error: (error: unknown) => {
          this.acting.set(false);
          this.failure.set(parseApiFailure(error));
        },
      });
    });
  }

  openItem(item?: RequestItem): void {
    const current = this.request();
    if (!current?.id) {
      return;
    }
    const dialog = this.dialogs.open<RequestItemDialogComponent, RequestItemDialogData, RequestItemDialogResult>(
      RequestItemDialogComponent,
      { width: '520px', data: { requestId: current.id, item } },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  removeItem(item: RequestItem): void {
    if (!item.id) {
      return;
    }
    this.requests.removeItem(item.id).subscribe({
      next: () => this.reload(),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }
}
