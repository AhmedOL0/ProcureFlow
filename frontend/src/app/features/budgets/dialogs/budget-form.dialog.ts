import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { BudgetsService } from '../budgets.service';
import { parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';

/**
 * Create a budget pot. Name+period must be unique (409 BUDGET_EXISTS);
 * currency is optional and defaults server-side to MAD.
 */
@Component({
  selector: 'app-budget-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './budget-form.dialog.html',
})
export class BudgetFormDialogComponent {
  private readonly budgets = inject(BudgetsService);
  private readonly dialog = inject(MatDialogRef<BudgetFormDialogComponent, boolean>);

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    period: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    amount: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)],
    }),
    currency: new FormControl('MAD', { nonNullable: true, validators: [Validators.maxLength(3)] }),
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
    const currency = value.currency.trim().toUpperCase();
    this.budgets
      .create({
        name: value.name.trim(),
        period: value.period,
        amountMinor: Math.round(Number.parseFloat(value.amount) * 100),
        ...(currency === '' ? {} : { currency }),
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
