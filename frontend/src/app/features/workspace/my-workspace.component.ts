import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationCenterService } from '../../core/notifications/notification-center.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { Bell, FileText, Inbox, Plus } from '../../shared/icons';
import { ApiFailure, parseApiFailure } from '../../shared/utils/api-errors';
import { formatInstant } from '../../shared/utils/time';
import { ProcurementService, PurchaseRequest } from '../procurement/procurement.service';

/**
 * My workspace: the caller's own slice — requests they raised, unread
 * notifications, and the next action. Everything filters client-side over
 * the same contracts the directories use; assignments and delegations
 * stay on the approval dossier and inbox, which adjudicate server-side.
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
  styleUrl: './my-workspace.component.scss',
})
export class MyWorkspaceComponent {
  private readonly requests = inject(ProcurementService);
  private readonly auth = inject(AuthService);
  protected readonly inbox = inject(NotificationCenterService);

  protected readonly icons = { plus: Plus, file: FileText, bell: Bell, inbox: Inbox };
  protected readonly formatInstant = formatInstant;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');
  protected readonly user = this.auth.currentUser;

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  private readonly allRequests = signal<PurchaseRequest[]>([]);

  private readonly myUserId = computed(() => {
    const user = this.user() as { id?: string; userId?: string } | null;
    return user?.id ?? user?.userId ?? '';
  });

  protected readonly mine = computed(() => {
    const mine = this.myUserId();
    return this.allRequests().filter((row) => (row.requesterId ?? '') === mine);
  });

  protected readonly openMine = computed(() =>
    this.mine().filter((row) => row.status === 'DRAFT' || row.status === 'SUBMITTED'),
  );

  protected readonly recentMine = computed(() =>
    [...this.mine()]
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
      .slice(0, 3),
  );

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
    this.requests.list(null).subscribe({
      next: (rows) => {
        this.allRequests.set(rows ?? []);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }
}
