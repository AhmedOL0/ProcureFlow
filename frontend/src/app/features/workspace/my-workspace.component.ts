import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationCenterService } from '../../core/notifications/notification-center.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { Bell, FileText, Plus } from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatInstant } from '../../shared/utils/time';
import { ProcurementService, PurchaseRequest } from '../procurement/procurement.service';

/**
 * My workspace: the caller's own slice — requests they raised, unread
 * notifications, and the next action. The slice comes from the server-side
 * /mine contract (the requester is forced from the session); assignments
 * and delegations stay on the approval dossier and inbox, which adjudicate
 * server-side.
 */
@Component({
  selector: 'app-my-workspace',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './my-workspace.component.html',
})
export class MyWorkspaceComponent {
  private readonly requests = inject(ProcurementService);
  private readonly auth = inject(AuthService);
  protected readonly inbox = inject(NotificationCenterService);

  protected readonly icons = { plus: Plus, file: FileText, bell: Bell };
  protected readonly formatInstant = formatInstant;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');
  protected readonly user = this.auth.currentUser;

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  private readonly mineRows = signal<PurchaseRequest[]>([]);
  protected readonly mineTotal = signal(0);
  protected readonly openTotal = signal(0);

  protected readonly recentMine = computed(() => this.mineRows());

  protected readonly latestUnread = computed(() =>
    this.inbox
      .items()
      .filter((item) => !item.read)
      .slice(0, 5),
  );

  constructor() {
    this.reload();
    this.inbox.refresh();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    forkJoin({
      rows: this.requests.mine(null, '', 0, 5),
      draft: this.requests.mine('DRAFT', '', 0, 1),
      submitted: this.requests.mine('SUBMITTED', '', 0, 1),
    }).subscribe({
      next: ({ rows, draft, submitted }) => {
        this.mineRows.set(rows.rows);
        this.mineTotal.set(rows.total);
        this.openTotal.set(draft.total + submitted.total);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }
}
