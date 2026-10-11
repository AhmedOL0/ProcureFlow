import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type PurchaseRequest = components['schemas']['PurchaseRequestResponse'];
export type RequestItem = components['schemas']['ItemResponse'];
export type Decision = components['schemas']['DecisionResponse'];
export type ApprovalState = components['schemas']['AssignmentResponse'];

export interface RequestPage {
  rows: PurchaseRequest[];
  total: number;
}

/**
 * Thin typed client over procurement + approval-decision contracts.
 * Lifecycle rules (draft-only edits, submit needs an item, one decision)
 * live server-side; components render outcomes and adjudications.
 */
@Injectable({ providedIn: 'root' })
export class ProcurementService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  list(
    status: string | null,
    query: string | null,
    pageIndex: number,
    pageSize: number,
  ): Observable<RequestPage> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (status) {
      params = params.set('status', status);
    }
    if (query?.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http
      .get<components['schemas']['PagedPurchaseRequestResponse']>(`${this.base}/purchase-requests`, {
        params,
      })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  /**
   * The caller's own slice: the backend forces the requester from the
   * session, so no caller id ever crosses the wire.
   */
  mine(
    status: string | null,
    query: string | null,
    pageIndex: number,
    pageSize: number,
  ): Observable<RequestPage> {
    let params = new HttpParams().set('page', pageIndex).set('size', pageSize);
    if (status) {
      params = params.set('status', status);
    }
    if (query?.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http
      .get<components['schemas']['PagedPurchaseRequestResponse']>(`${this.base}/purchase-requests/mine`, {
        params,
      })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  get(id: string): Observable<PurchaseRequest> {
    return this.http.get<PurchaseRequest>(`${this.base}/purchase-requests/${id}`);
  }

  create(
    body: components['schemas']['CreatePurchaseRequestRequest'],
    idempotencyKey: string,
  ): Observable<PurchaseRequest> {
    return this.http.post<PurchaseRequest>(`${this.base}/purchase-requests`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  update(id: string, body: components['schemas']['UpdatePurchaseRequestRequest']): Observable<PurchaseRequest> {
    return this.http.patch<PurchaseRequest>(`${this.base}/purchase-requests/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/purchase-requests/${id}`);
  }

  submit(id: string): Observable<PurchaseRequest> {
    return this.http.post<PurchaseRequest>(`${this.base}/purchase-requests/${id}/submit`, {});
  }

  cancel(id: string): Observable<PurchaseRequest> {
    return this.http.post<PurchaseRequest>(`${this.base}/purchase-requests/${id}/cancel`, {});
  }

  addItem(id: string, body: components['schemas']['ItemInputDto']): Observable<RequestItem> {
    return this.http.post<RequestItem>(`${this.base}/purchase-requests/${id}/items`, body);
  }

  updateItem(itemId: string, body: components['schemas']['UpdateItemRequest']): Observable<RequestItem> {
    return this.http.patch<RequestItem>(`${this.base}/purchase-request-items/${itemId}`, body);
  }

  removeItem(itemId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/purchase-request-items/${itemId}`);
  }

  decide(requestId: string, approved: boolean, comment?: string): Observable<Decision> {
    return this.http.post<Decision>(`${this.base}/approvals/decisions`, {
      requestId,
      decision: approved ? 'APPROVED' : 'REJECTED',
      ...(comment?.trim() ? { comment: comment.trim() } : {}),
    });
  }

  decisionFor(requestId: string): Observable<Decision> {
    return this.http.get<Decision>(`${this.base}/approvals/decisions`, { params: { requestId } });
  }

  approvalState(requestId: string): Observable<ApprovalState> {
    return this.http.get<ApprovalState>(`${this.base}/approvals/state`, { params: { requestId } });
  }
}
