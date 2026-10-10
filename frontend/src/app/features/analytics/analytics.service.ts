import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type SpendResponse = components['schemas']['SpendResponse'];
export type SpendMonth = components['schemas']['MonthResponse'];
export type SupplierKpi = components['schemas']['SupplierKpiResponse'];
export type ApprovalKpi = components['schemas']['ApprovalKpiResponse'];
export type AiUsage = components['schemas']['UsageResponse'];

export { toSpendCategories } from './spend-categories';
export type { SpendCategory } from './spend-categories';

/**
 * Read-only analytics over real aggregates. All three KPI endpoints need
 * analytics:read; the AI usage endpoint answers 200 even while the copilot
 * itself is disabled (AI_ENABLED=false makes chat/explain/extract/compare
 * answer 503 AI_DISABLED, so no chat UI ships until the backend enables it).
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1`;

  spend(period: string | null): Observable<SpendResponse> {
    let params = new HttpParams();
    if (period) {
      params = params.set('period', period);
    }
    return this.http.get<SpendResponse>(`${this.base}/analytics/spend`, { params });
  }

  supplierKpis(): Observable<SupplierKpi[]> {
    return this.http.get<SupplierKpi[]>(`${this.base}/analytics/suppliers`);
  }

  approvalKpis(): Observable<ApprovalKpi> {
    return this.http.get<ApprovalKpi>(`${this.base}/analytics/approvals`);
  }

  aiUsage(period: string | null): Observable<AiUsage> {
    let params = new HttpParams();
    if (period) {
      params = params.set('period', period);
    }
    return this.http.get<AiUsage>(`${this.base}/ai/usage`, { params });
  }

  currentPeriodUtc(): string {
    return new Date().toISOString().slice(0, 7);
  }
}
