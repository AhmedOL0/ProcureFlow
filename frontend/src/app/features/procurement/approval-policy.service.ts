import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';

export type Workflow = components['schemas']['WorkflowResponse'];
export type WorkflowStep = components['schemas']['StepResponse'];
export type Delegation = components['schemas']['DelegationResponse'];
export type CreateWorkflow = components['schemas']['CreateWorkflowRequest'];
export type UpdateWorkflow = components['schemas']['UpdateWorkflowRequest'];

/**
 * Approval lanes, steps and delegations. Everything here needs
 * procurement:approve — ordinary requesters never reach these endpoints.
 * Escalation itself has no endpoint (escalateAfterDays + scheduler only),
 * so the UI edits the threshold instead of faking an escalate button.
 */
@Injectable({ providedIn: 'root' })
export class ApprovalPolicyService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/approvals`;

  workflows(): Observable<Workflow[]> {
    return this.http.get<Workflow[]>(`${this.base}/workflows`);
  }

  createWorkflow(body: CreateWorkflow): Observable<Workflow> {
    return this.http.post<Workflow>(`${this.base}/workflows`, body);
  }

  updateWorkflow(id: string, body: UpdateWorkflow): Observable<Workflow> {
    return this.http.patch<Workflow>(`${this.base}/workflows/${id}`, body);
  }

  steps(id: string): Observable<WorkflowStep[]> {
    return this.http.get<WorkflowStep[]>(`${this.base}/workflows/${id}/steps`);
  }

  addStep(id: string, stepOrder: number, approverId: string): Observable<WorkflowStep> {
    return this.http.post<WorkflowStep>(`${this.base}/workflows/${id}/steps`, { stepOrder, approverId });
  }

  removeStep(id: string, stepId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/workflows/${id}/steps/${stepId}`);
  }

  delegations(): Observable<Delegation[]> {
    return this.http.get<Delegation[]>(`${this.base}/delegations`);
  }

  delegate(delegateId: string, endsAt: string): Observable<Delegation> {
    return this.http.post<Delegation>(`${this.base}/delegations`, { delegateId, endsAt });
  }

  revokeDelegation(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/delegations/${id}`);
  }
}
