import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type ChatAnswer = components['schemas']['ChatResponse'];
export type RequestDraft = components['schemas']['DraftResponse'];
export type DraftItem = components['schemas']['DraftItemResponse'];
export type Extraction = components['schemas']['ExtractionResponse'];
export type ExtractionCreation = components['schemas']['ExtractionRequestResponse'];

export interface ExtractionPage {
  rows: Extraction[];
  total: number;
}

/**
 * ProcureAI copilot calls. Every action needs ai:use and a live provider:
 * while AI_ENABLED=false the backend answers 503 AI_DISABLED and the UI
 * surfaces that message instead of faking answers. Answers always carry
 * KPI citations — render them, never strip them.
 */
@Injectable({ providedIn: 'root' })
export class CopilotService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/ai`;

  chat(question: string): Observable<ChatAnswer> {
    return this.http.post<ChatAnswer>(`${this.base}/chat`, { question });
  }

  explainSpend(period: string | null): Observable<ChatAnswer> {
    return this.http.post<ChatAnswer>(`${this.base}/explain-spend`, { period });
  }

  extractRequest(narrative: string): Observable<RequestDraft> {
    return this.http.post<RequestDraft>(`${this.base}/extract-request`, { question: narrative });
  }

  compareQuotations(quotes: { supplier: string; amountMinor: number }[]): Observable<ChatAnswer> {
    return this.http.post<ChatAnswer>(`${this.base}/compare-quotations`, { quotes });
  }

  /**
   * Validated extraction history, newest first. Derived store with 90-day
   * retention: an empty shelf is a real empty shelf (reads 503 when the
   * store itself is unreachable).
   */
  history(): Observable<ExtractionPage> {
    const params = new HttpParams().set('page', 0).set('size', 20);
    return this.http
      .get<components['schemas']['PagedExtractionResponse']>(`${this.base}/extractions`, { params })
      .pipe(map((result) => ({ rows: result?.content ?? [], total: result?.totalElements ?? 0 })));
  }

  /**
   * Creates a real draft from a stored extraction through the guarded,
   * idempotent server path (single-currency, validation, X-Replay on
   * reuse). The server links the extraction to the draft.
   */
  createFromExtraction(id: string, idempotencyKey: string): Observable<ExtractionCreation> {
    return this.http.post<ExtractionCreation>(`${this.base}/extractions/${id}/request`, null, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
}
