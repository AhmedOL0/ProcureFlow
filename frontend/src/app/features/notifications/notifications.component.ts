import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LucideAngularModule } from 'lucide-angular';

import { NotificationCenterService } from '../../core/notifications/notification-center.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Bell } from '../../shared/icons';
import { formatInstant } from '../../shared/utils/time';

/**
 * Notification history: every event the workspace raised for the caller,
 * newest first. Rows carry the event text only — the contract has no
 * request deep-link, so none is invented. Mark-all-read fans out to one
 * PATCH per unread row (no bulk endpoint exists).
 */
@Component({
  selector: 'app-notifications',
  imports: [
    MatButtonModule, MatChipsModule, MatProgressSpinnerModule, LucideAngularModule,
    PageHeaderComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent {
  protected readonly inbox = inject(NotificationCenterService);
  protected readonly icons = { bell: Bell };
  protected readonly formatInstant = formatInstant;

  protected readonly unreadOnly = signal(false);
  protected readonly visible = computed(() =>
    this.unreadOnly() ? this.inbox.items().filter((item) => !item.read) : this.inbox.items(),
  );

  constructor() {
    this.inbox.refresh();
  }

  showAll(): void {
    this.unreadOnly.set(false);
  }

  showUnread(): void {
    this.unreadOnly.set(true);
  }
}
