import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { formatMinor } from '../../../shared/utils/money';

export interface PayDialogData {
  remaining: number;
  currency: string;
}

/** Record a payment up to the remaining balance. Overpaying is rejected. */
@Component({
  selector: 'app-pay-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule],
  templateUrl: './pay.dialog.html',
})
export class PayDialogComponent {
  private readonly dialog = inject(MatDialogRef<PayDialogComponent, PayDialogResult>);
  protected readonly data = inject<PayDialogData>(MAT_DIALOG_DATA);

  readonly amount = new FormControl<number>(0, {
    nonNullable: true,
    validators: [Validators.required, Validators.min(1)],
  });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);
  protected readonly formatMinor = formatMinor;

  save(): void {
    if (this.amount.invalid || this.busy()) {
      this.amount.markAsTouched();
      return;
    }
    if (this.amount.value > this.data.remaining) {
      this.failure.set(
        `That exceeds the remaining balance of ${formatMinor(this.data.remaining, this.data.currency)}.`,
      );
      return;
    }
    this.busy.set(true);
    this.dialog.close({ amountMinor: Math.round(this.amount.value) });
  }
}

export interface PayDialogResult {
  amountMinor: number;
}
