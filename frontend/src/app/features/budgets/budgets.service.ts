import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Budget = components['schemas']['BudgetResponse'];
export type Reservation = components['schemas']['ReservationResponse'];
export type CreateBudget = components['schemas']['CreateBudgetRequest'];

/**
 * Full pot management over the budgets contract. Reads need budget:read,
 * creates and deletes need budget:manage — the backend adjudicates both.
 * There is no rename/topup endpoint; pots are created per name+period and
 * deleted only when no reservation holds on them (409 otherwise).
 */
@Injectable({ providedIn: 'root' })
export class BudgetsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  list(period: string | null): Observable<Budget[]> {
    let params = new HttpParams();
    if (period) {
      params = params.set('period', period);
    }
    return this.http.get<Budget[]>(`${this.base}/budgets`, { params });
  }

  get(id: string): Observable<Budget> {
    return this.http.get<Budget>(`${this.base}/budgets/${id}`);
  }

  reservations(id: string): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.base}/budgets/${id}/reservations`);
  }

  create(body: CreateBudget): Observable<Budget> {
    return this.http.post<Budget>(`${this.base}/budgets`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/budgets/${id}`);
  }

  currentPeriodUtc(): string {
    return new Date().toISOString().slice(0, 7);
  }

  /** Last six UTC months, newest first, for period quick-chips. */
  recentPeriods(count = 6): string[] {
    const out: string[] = [];
    const cursor = new Date();
    cursor.setUTCDate(1);
    for (let i = 0; i < count; i += 1) {
      out.push(cursor.toISOString().slice(0, 7));
      cursor.setUTCMonth(cursor.getUTCMonth() - 1);
    }
    return out;
  }
}
