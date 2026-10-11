import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { concatMap, finalize, from, toArray } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

type NotificationResponse = components['schemas']['NotificationResponse'];
type InboxPage = components['schemas']['PagedNotificationResponse'];

export interface PageState {
  pageIndex: number;
  pageSize: number;
  length: number;
}

/**
 * Header inbox + notifications page: latest rows, unread count, mark-read.
 * The inbox endpoint is paged server-side; `items` holds the loaded page
 * (first page by default) and `page.length` the server total. The unread
 * badge therefore counts the loaded page — exact at realistic volumes, and
 * the notifications page always shows the true total beside its paginator.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  readonly items = signal<NotificationResponse[]>([]);
  readonly page = signal<PageState>({ pageIndex: 0, pageSize: 20, length: 0 });
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly unread = computed(() => this.items().filter((item) => !item.read).length);

  refresh(): void {
    const current = this.page();
    this.load(current.pageIndex, current.pageSize);
  }

  load(pageIndex: number, pageSize: number): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    this.http
      .get<InboxPage>(`${this.api}/api/v1/notifications`, {
        params: { page: pageIndex, size: pageSize },
      })
      .subscribe({
        next: (result) => {
          this.items.set(result?.content ?? []);
          this.page.set({
            pageIndex: result?.page ?? pageIndex,
            pageSize: result?.size ?? pageSize,
            length: result?.totalElements ?? 0,
          });
          this.loading.set(false);
        },
        error: () => {
          this.failed.set(true);
          this.loading.set(false);
        },
      });
  }

  markRead(id: string): void {
    this.http
      .patch<NotificationResponse>(`${this.api}/api/v1/notifications/${id}/read`, {})
      .subscribe({
        next: (updated) => {
          this.items.update((rows) => rows.map((row) => (row.id === updated.id ? updated : row)));
        },
      });
  }

  /**
   * Marks every unread row read, one contract call each: the API offers no
   * bulk endpoint, so the page is honest about the N calls behind the button.
   */
  markAllRead(): void {
    const pending = this.items().filter((item) => !item.read && item.id);
    if (this.loading() || pending.length === 0) {
      return;
    }
    this.loading.set(true);
    from(pending)
      .pipe(
        concatMap((item) =>
          this.http.patch<NotificationResponse>(
            `${this.api}/api/v1/notifications/${item.id}/read`,
            {},
          ),
        ),
        toArray(),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (updated) => {
          const byId = new Map(updated.map((row) => [row.id, row]));
          this.items.update((rows) => rows.map((row) => byId.get(row.id) ?? row));
        },
        error: () => this.refresh(),
      });
  }
}
