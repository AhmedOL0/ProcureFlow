import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

type NotificationResponse = components['schemas']['NotificationResponse'];

/** Header inbox: latest rows, unread count, per-item mark-read. */
@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  readonly items = signal<NotificationResponse[]>([]);
  readonly loading = signal(false);
  readonly unread = computed(() => this.items().filter((item) => !item.read).length);

  refresh(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.http.get<NotificationResponse[]>(`${this.api}/api/v1/notifications`).subscribe({
      next: (rows) => {
        this.items.set(rows ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  markRead(id: string): void {
    this.http.patch<NotificationResponse>(`${this.api}/api/v1/notifications/${id}/read`, {}).subscribe({
      next: (updated) => {
        this.items.update((rows) => rows.map((row) => (row.id === updated.id ? updated : row)));
      },
    });
  }
}
