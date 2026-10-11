import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type PurchaseOrder = components['schemas']['OrderResponse'];
export type OrderLine = components['schemas']['OrderLineResponse'];

export interface OrderPage {
  rows: PurchaseOrder[];
  total: number;
}

/**
 * Thin typed client over the purchase-order contract. Creation needs an
 * APPROVED request (the backend refuses anything else); sending flips the
 * request to ORDERED in the same transaction. No client-side lifecycle
 * guesses — transitions the backend rejects surface as 409s.
 */
@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  list(status: string | null, pageIndex: number, pageSize: number): Observable<OrderPage> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (status) {
      params = params.set('status', status);
    }
    return this.http
      .get<components['schemas']['PagedOrderResponse']>(`${this.base}/purchase-orders`, { params })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  get(id: string): Observable<PurchaseOrder> {
    return this.http.get<PurchaseOrder>(`${this.base}/purchase-orders/${id}`);
  }

  create(requestId: string, supplierId: string): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/purchase-orders`, { requestId, supplierId });
  }

  send(id: string): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/purchase-orders/${id}/send`, {});
  }

  receive(id: string, lines: { itemId: string; quantity: number }[]): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/purchase-orders/${id}/receive`, { lines });
  }

  close(id: string): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/purchase-orders/${id}/close`, {});
  }

  cancel(id: string): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.base}/purchase-orders/${id}/cancel`, {});
  }
}
