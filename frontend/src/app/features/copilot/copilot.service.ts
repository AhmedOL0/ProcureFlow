import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type ChatAnswer = components['schemas']['ChatResponse'];
export type RequestDraft = components['schemas']['DraftResponse'];
export type DraftItem = components['schemas']['DraftItemResponse'];

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
}
