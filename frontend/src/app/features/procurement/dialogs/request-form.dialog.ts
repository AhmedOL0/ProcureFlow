import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ProcurementService, PurchaseRequest } from '../procurement.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

export interface RequestFormData {
  mode: 'create' | 'edit';
  request?: PurchaseRequest;
}

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const;

/** Create/edit request header. Items are managed on the dossier, not here. */
@Component({
  selector: 'app-request-form-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './request-form.dialog.html',
})
export class RequestFormDialogComponent {
  private readonly requests = inject(ProcurementService);
  private readonly dialog = inject(MatDialogRef<RequestFormDialogComponent, RequestFormResult>);
  protected readonly data = inject<RequestFormData>(MAT_DIALOG_DATA);

  readonly priorities = PRIORITIES;
  readonly form = new FormGroup({
    title: new FormControl(this.data.request?.title ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    description: new FormControl(this.data.request?.description ?? '', { nonNullable: true }),
    priority: new FormControl<string>(this.data.request?.priority ?? 'MEDIUM', { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  protected isCreate(): boolean {
    return this.data.mode === 'create';
  }

  save(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const value = this.form.getRawValue();
    const idempotencyKey =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
    const request = this.isCreate() || !this.data.request?.id
      ? this.requests.create(
          {
            title: value.title.trim(),
            description: value.description.trim() || undefined,
            priority: value.priority as 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT',
          },
          idempotencyKey,
        )
      : this.requests.update(this.data.request.id, {
          title: value.title.trim(),
          description: value.description.trim(),
          priority: value.priority as 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT',
        });
    request.subscribe({
      next: (saved) => this.dialog.close({ id: saved.id ?? null }),
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }
}

export interface RequestFormResult {
  id: string | null;
}
