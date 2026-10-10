import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ProcurementService } from '../procurement.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const;

/**
 * Request creation as a real page (deep-linkable, no modal-on-load).
 * Creates the draft, then lands on the dossier where lines are added.
 */
@Component({
  selector: 'app-request-create',
  imports: [RouterLink, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule, PageHeaderComponent],
  templateUrl: './request-create.component.html',
  styleUrl: './request-create.component.scss',
})
export class RequestCreateComponent {
  private readonly requests = inject(ProcurementService);
  private readonly router = inject(Router);

  readonly priorities = PRIORITIES;
  readonly form = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    description: new FormControl('', { nonNullable: true }),
    priority: new FormControl<string>('MEDIUM', { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

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
    this.requests
      .create(
        {
          title: value.title.trim(),
          description: value.description.trim() || undefined,
          priority: value.priority as 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT',
        },
        idempotencyKey,
      )
      .subscribe({
        next: (saved) => {
          if (saved.id) {
            void this.router.navigate(['/requests', saved.id]);
          }
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.failure.set(userMessageFor(parseApiFailure(error)));
        },
      });
  }
}
