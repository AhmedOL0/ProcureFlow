import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SuppliersService } from '../suppliers.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

export interface PerformanceDialogData {
  supplierId: string;
}

/** Record one monthly scorecard (YYYY-MM, 0–100 rates). */
@Component({
  selector: 'app-performance-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule],
  templateUrl: './performance.dialog.html',
  styleUrl: './dialogs.scss',
})
export class PerformanceDialogComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<PerformanceDialogComponent, boolean>);
  protected readonly data = inject<PerformanceDialogData>(MAT_DIALOG_DATA);

  readonly form = new FormGroup({
    period: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)],
    }),
    onTimeRate: new FormControl<number | null>(null, {
      validators: [Validators.min(0), Validators.max(100)],
    }),
    qualityScore: new FormControl<number | null>(null, {
      validators: [Validators.min(0), Validators.max(100)],
    }),
    notes: new FormControl('', { nonNullable: true }),
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
    this.suppliers
      .recordPerformance(this.data.supplierId, {
        period: value.period,
        ...(value.onTimeRate !== null ? { onTimeRate: value.onTimeRate } : {}),
        ...(value.qualityScore !== null ? { qualityScore: value.qualityScore } : {}),
        ...(value.notes.trim() ? { notes: value.notes.trim() } : {}),
      })
      .subscribe({
        next: () => this.dialog.close(true),
        error: (error: unknown) => {
          this.busy.set(false);
          this.failure.set(userMessageFor(parseApiFailure(error)));
        },
      });
  }
}
