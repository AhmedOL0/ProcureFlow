import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Invoice = components['schemas']['InvoiceResponse'];
export type InvoiceMatchLine = components['schemas']['MatchLineResponse'];

/**
 * Thin typed client over the invoicing contract. Status is derived
 * server-side from payments (UNPAID → PARTIAL → PAID); recording a payment
 * is the only transition, and overpaying is rejected with 409. This UI
 * tracks payment status only — it never moves money.
 */
@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  list(status: string | null): Observable<Invoice[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Invoice[]>(`${this.base}/invoices`, { params });
  }

  get(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.base}/invoices/${id}`);
  }

  create(orderId: string, number: string, lines: { orderItemId: string; quantity: number }[]): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.base}/invoices`, { orderId, number, lines });
  }

  pay(id: string, amountMinor: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.base}/invoices/${id}/payments`, { amountMinor });
  }
}
