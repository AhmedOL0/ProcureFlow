import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { Trash2, Wallet } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { formatMinor } from '../../../shared/utils/money';
import { formatInstant } from '../../../shared/utils/time';
import { Budget, BudgetsService, Reservation } from '../budgets.service';

/**
 * Budget dossier: allocation, reservation and remainder with the utilization
 * bar, plus every hold approved requests keep on the pot. Deleting is
 * refused by the backend (409) while any reservation holds — the message
 * says so instead of failing silently.
 */
@Component({
  selector: 'app-budget-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatTableModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './budget-detail.component.html',
  styleUrl: './budget-detail.component.scss',
})
export class BudgetDetailComponent {
  private readonly budgets = inject(BudgetsService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialogs = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly icons = { trash: Trash2, wallet: Wallet };
  protected readonly formatMinor = formatMinor;
  protected readonly formatInstant = formatInstant;
  protected readonly canManage = this.auth.hasAuthority('budget:manage');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly pot = signal<Budget | null>(null);
  protected readonly holds = signal<Reservation[]>([]);
  protected readonly deleting = signal(false);
  protected readonly holdColumns = ['request', 'amount', 'held'];

  constructor() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    forkJoin({ pot: this.budgets.get(id), holds: this.budgets.reservations(id) }).subscribe({
      next: ({ pot, holds }) => {
        this.pot.set(pot);
        this.holds.set(holds ?? []);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  utilization(): number {
    const pot = this.pot();
    const amount = pot?.amountMinor ?? 0;
    if (!pot || amount <= 0) {
      return 0;
    }
    return Math.min(100, ((pot.reservedMinor ?? 0) / amount) * 100);
  }

  backToList(): void {
    void this.router.navigate(['/budgets']);
  }

  confirmDelete(): void {
    const pot = this.pot();
    if (!pot?.id || this.deleting()) {
      return;
    }
    const dialog = this.dialogs.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Delete this pot?',
        message: `“${pot.name}” (${pot.period}) disappears. Pots holding reservations cannot be deleted.`,
        confirmLabel: 'Delete pot',
      },
    });
    dialog.afterClosed().subscribe((confirmed) => {
      if (confirmed === true) {
        this.delete(pot.id as string);
      }
    });
  }

  private delete(id: string): void {
    this.deleting.set(true);
    this.budgets.remove(id).subscribe({
      next: () => void this.router.navigate(['/budgets']),
      error: (error: unknown) => {
        this.deleting.set(false);
        this.snack.open(userMessageFor(parseApiFailure(error)), 'Dismiss', { duration: 6000 });
      },
    });
  }
}
