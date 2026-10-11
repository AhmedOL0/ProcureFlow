import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Invoice = components['schemas']['InvoiceResponse'];
export type InvoiceMatchLine = components['schemas']['MatchLineResponse'];

export interface InvoicePage {
  rows: Invoice[];
  total: number;
}

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

  list(
    status: string | null,
    query: string | null,
    pageIndex: number,
    pageSize: number,
  ): Observable<InvoicePage> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (status) {
      params = params.set('status', status);
    }
    if (query?.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http
      .get<components['schemas']['PagedInvoiceResponse']>(`${this.base}/invoices`, { params })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
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
