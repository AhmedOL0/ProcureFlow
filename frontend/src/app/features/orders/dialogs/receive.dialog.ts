import { Component, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { OrderLine } from '../orders.service';

export interface ReceiveDialogData {
  lines: OrderLine[];
}

/** Record goods receipt per line; quantities beyond the remainder are rejected server-side. */
@Component({
  selector: 'app-receive-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './receive.dialog.html',
})
export class ReceiveDialogComponent {
  private readonly dialog = inject(MatDialogRef<ReceiveDialogComponent, ReceiveDialogResult>);
  protected readonly data = inject<ReceiveDialogData>(MAT_DIALOG_DATA);

  readonly form = new FormGroup({
    quantities: new FormArray(
      this.data.lines.map(
        (line) =>
          new FormGroup({
            itemId: new FormControl(line.id ?? '', { nonNullable: true }),
            outstanding: new FormControl((line.quantity ?? 0) - (line.receivedQty ?? 0), {
              nonNullable: true,
            }),
            quantity: new FormControl(0, {
              nonNullable: true,
              validators: [
                Validators.required,
                Validators.min(0),
                Validators.max((line.quantity ?? 0) - (line.receivedQty ?? 0)),
              ],
            }),
          }),
      ),
    ),
  });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  protected rows(): {
    itemId: string;
    description: string;
    outstanding: number;
    control: FormControl<number>;
  }[] {
    return this.data.lines.map((line, index) => ({
      itemId: line.id ?? '',
      description: line.description ?? '?',
      outstanding: (line.quantity ?? 0) - (line.receivedQty ?? 0),
      control: this.form.controls.quantities.at(index).controls.quantity,
    }));
  }

  save(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const lines = this.rows()
      .filter((row) => row.control.value > 0 && row.outstanding > 0)
      .map((row) => ({
        itemId: row.itemId,
        quantity: Math.min(row.control.value, row.outstanding),
      }));
    if (lines.length === 0) {
      this.failure.set('Enter a received quantity for at least one outstanding line.');
      return;
    }
    this.busy.set(true);
    this.dialog.close({ lines });
  }
}

export interface ReceiveDialogResult {
  lines: { itemId: string; quantity: number }[];
}
