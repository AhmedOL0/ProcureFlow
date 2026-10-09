import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Budget = components['schemas']['BudgetResponse'];

/** Read-only budget context for the approval story. Full pot management is a later slice. */
@Injectable({ providedIn: 'root' })
export class BudgetContextService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  /** Pots for one UTC month, e.g. "2026-10". 403 when the caller lacks budget:read. */
  pots(period: string): Observable<Budget[]> {
    const params = new HttpParams().set('period', period);
    return this.http.get<Budget[]>(`${this.base}/budgets`, { params });
  }

  currentPeriodUtc(): string {
    return new Date().toISOString().slice(0, 7);
  }
}
