import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ScrollText } from '../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';
import { formatMinor } from '../../shared/utils/money';
import { formatInstant } from '../../shared/utils/time';
import {
  ApprovalPolicyService,
  Delegation,
  Workflow,
  WorkflowStep,
} from '../procurement/approval-policy.service';
import { OrganizationService, WorkspaceUser } from './organization.service';

/**
 * Approval policies: amount lanes with ordered approver steps, plus bounded
 * delegations. Escalation has no endpoint — the lane threshold
 * (escalateAfterDays) is editable and the scheduler enforces it. Approver
 * pickers list workspace users only for user managers; everyone else types
 * the approver UUID the directory gave them.
 */
@Component({
  selector: 'app-workflows',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './workflows.component.html',
})
export class WorkflowsComponent {
  private readonly policies = inject(ApprovalPolicyService);
  private readonly org = inject(OrganizationService);
  private readonly auth = inject(AuthService);

  protected readonly icons = { lanes: ScrollText };
  protected readonly formatMinor = formatMinor;
  protected readonly formatInstant = formatInstant;
  protected readonly canApprove = this.auth.hasAuthority('procurement:approve');
  private readonly canSeeUsers = this.auth.hasAuthority('user:manage');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly lanes = signal<Workflow[]>([]);
  protected readonly steps = signal<WorkflowStep[]>([]);
  protected readonly selectedLane = signal<Workflow | null>(null);
  protected readonly delegations = signal<Delegation[]>([]);
  protected readonly users = signal<WorkspaceUser[]>([]);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);

  readonly laneForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    minAmount: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)],
    }),
    maxAmount: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(/^\d+(\.\d{1,2})?$/)],
    }),
    escalateAfterDays: new FormControl('3', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d+$/)],
    }),
  });
  readonly stepApprover = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly delegateId = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly delegateUntil = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.notice.set(null);
    forkJoin({
      lanes: this.policies.workflows(),
      delegations: this.policies.delegations(),
      users: this.canSeeUsers ? this.org.users() : of([]),
    }).subscribe({
      next: ({ lanes, delegations, users }) => {
        this.lanes.set(lanes ?? []);
        this.delegations.set(delegations ?? []);
        this.users.set(users ?? []);
        const selected = this.selectedLane();
        const kept = (lanes ?? []).find((lane) => lane.id === selected?.id) ?? null;
        this.selectedLane.set(kept);
        this.loading.set(false);
        if (kept?.id) {
          this.loadSteps(kept.id);
        } else {
          this.steps.set([]);
        }
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  selectLane(lane: Workflow): void {
    this.selectedLane.set(lane);
    if (lane.id) {
      this.loadSteps(lane.id);
    }
  }

  loadSteps(laneId: string): void {
    this.policies.steps(laneId).subscribe({
      next: (rows) =>
        this.steps.set((rows ?? []).sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  createLane(): void {
    if (this.laneForm.invalid || this.busy()) {
      this.laneForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const value = this.laneForm.getRawValue();
    const max = value.maxAmount.trim();
    this.policies
      .createWorkflow({
        name: value.name.trim(),
        minAmountMinor: Math.round(Number.parseFloat(value.minAmount) * 100),
        ...(max === '' ? {} : { maxAmountMinor: Math.round(Number.parseFloat(max) * 100) }),
        escalateAfterDays: Number.parseInt(value.escalateAfterDays, 10),
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.laneForm.reset({ escalateAfterDays: '3' });
          this.notice.set('Lane created.');
          this.reload();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.failure.set(parseApiFailure(error));
        },
      });
  }

  setLaneActive(lane: Workflow, active: boolean): void {
    if (!lane.id) {
      return;
    }
    this.policies.updateWorkflow(lane.id, { active }).subscribe({
      next: () => this.reload(),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  addStep(): void {
    const lane = this.selectedLane();
    const approverId = this.stepApprover.value.trim();
    if (!lane?.id || !approverId) {
      return;
    }
    const order = this.steps().reduce((max, step) => Math.max(max, step.stepOrder ?? 0), 0) + 1;
    this.policies.addStep(lane.id, order, approverId).subscribe({
      next: () => {
        this.stepApprover.reset('');
        this.notice.set(`Step ${order} added.`);
        this.loadSteps(lane.id as string);
      },
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  removeStep(step: WorkflowStep): void {
    const lane = this.selectedLane();
    if (!lane?.id || !step.id) {
      return;
    }
    this.policies.removeStep(lane.id, step.id).subscribe({
      next: () => this.loadSteps(lane.id as string),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  createDelegation(): void {
    const delegateId = this.delegateId.value.trim();
    const until = this.delegateUntil.value;
    if (!delegateId || !until) {
      return;
    }
    const endsAt = new Date(until).toISOString();
    if (Number.isNaN(Date.parse(endsAt))) {
      this.delegateUntil.setErrors({ invalid: true });
      this.delegateUntil.markAsTouched();
      return;
    }
    this.policies.delegate(delegateId, endsAt).subscribe({
      next: () => {
        this.delegateId.reset('');
        this.delegateUntil.reset('');
        this.notice.set('Delegation granted.');
        this.reload();
      },
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  revokeDelegation(delegation: Delegation): void {
    if (!delegation.id) {
      return;
    }
    this.policies.revokeDelegation(delegation.id).subscribe({
      next: () => this.reload(),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  protected resolveUser(userId: string | null | undefined): string {
    if (!userId) {
      return '?';
    }
    return this.users().find((u) => u.id === userId)?.email ?? userId.slice(0, 8);
  }

  protected laneRange(lane: Workflow): string {
    const min = formatMinor(lane.minAmountMinor);
    return lane.maxAmountMinor === null || lane.maxAmountMinor === undefined
      ? `${min} and up`
      : `${min} – ${formatMinor(lane.maxAmountMinor)}`;
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }
}
