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
 * Header inbox + notifications page: latest rows, unread total, mark-read.
 * The inbox endpoint is paged server-side; `items` holds the loaded page
 * (first page by default) and `page.length` the server total. The unread
 * badge reads `unreadTotal` — a size-1 `unreadOnly` call whose total is the
 * server-wide unread count, so the badge holds past the first page. The
 * notifications page shows its own totals beside the paginator.
 * The read filter travels as `unreadOnly`; shell and workspace always read
 * the full inbox and pass nothing.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  readonly items = signal<NotificationResponse[]>([]);
  readonly page = signal<PageState>({ pageIndex: 0, pageSize: 20, length: 0 });
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly unreadTotal = signal(0);
  /** Unread rows on the loaded page: what "mark all read" will actually mark. */
  readonly unreadOnPage = computed(() => this.items().filter((item) => !item.read).length);

  constructor() {
    this.refreshUnreadTotal();
  }

  refresh(unreadOnly = false): void {
    const current = this.page();
    this.load(current.pageIndex, current.pageSize, unreadOnly);
  }

  load(pageIndex: number, pageSize: number, unreadOnly = false): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    this.http
      .get<InboxPage>(`${this.api}/api/v1/notifications`, {
        params: { page: pageIndex, size: pageSize, ...(unreadOnly ? { unreadOnly: true } : {}) },
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
          this.refreshUnreadTotal();
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
          this.refreshUnreadTotal();
        },
        error: () => this.refresh(),
      });
  }

  /**
   * Server-wide unread count via a size-1 `unreadOnly` read: one cheap query
   * whose total is exact. Failures keep the last known value — a badge must
   * never flash zero on a blip.
   */
  refreshUnreadTotal(): void {
    this.http
      .get<InboxPage>(`${this.api}/api/v1/notifications`, {
        params: { page: 0, size: 1, unreadOnly: true },
      })
      .subscribe({
        next: (result) => this.unreadTotal.set(result?.totalElements ?? 0),
        error: () => undefined,
      });
  }
}
