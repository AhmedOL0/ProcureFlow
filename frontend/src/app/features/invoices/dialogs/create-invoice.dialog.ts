import { Component, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { InvoiceService } from '../invoices.service';
import { OrderLine, OrderService, PurchaseOrder } from '../../orders/orders.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

const INVOICABLE = ['SENT', 'PARTIALLY_RECEIVED', 'RECEIVED'];

/** Book an invoice against a sent order's lines. Over-invoicing is rejected. */
@Component({
  selector: 'app-create-invoice-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './create-invoice.dialog.html',
})
export class CreateInvoiceDialogComponent {
  private readonly invoices = inject(InvoiceService);
  private readonly orders = inject(OrderService);
  private readonly dialog = inject(MatDialogRef<CreateInvoiceDialogComponent, CreateInvoiceDialogResult>);

  readonly orderControl = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly numberControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(50)],
  });
  readonly quantities = signal<{ line: OrderLine; control: FormControl<number> }[]>([]);
  readonly candidates = signal<PurchaseOrder[]>([]);
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  constructor() {
    this.orders.list(null).subscribe({
      next: (rows) => {
        const open = (rows ?? []).filter((o) => INVOICABLE.includes(o.status ?? ''));
        this.candidates.set(open);
        if (open.length === 1 && open[0]?.id) {
          this.orderControl.setValue(open[0]?.id ?? '');
        }
      },
      error: (error: unknown) => this.failure.set(userMessageFor(parseApiFailure(error))),
    });
    this.orderControl.valueChanges.subscribe((id) => this.loadLines(id));
  }

  private loadLines(orderId: string): void {
    this.quantities.set([]);
    if (!orderId) {
      return;
    }
    this.orders.get(orderId).subscribe({
      next: (order) => {
        this.quantities.set(
          (order.lines ?? []).map((line) => ({
            line,
            control: new FormControl(0, {
              nonNullable: true,
              validators: [Validators.required, Validators.min(0)],
            }),
          })),
        );
      },
      error: (error: unknown) => this.failure.set(userMessageFor(parseApiFailure(error))),
    });
  }

  save(): void {
    if (!this.orderControl.value || !this.numberControl.value.trim() || this.busy()) {
      return;
    }
    const lines = this.quantities()
      .filter((row) => row.control.value > 0 && row.line.id)
      .map((row) => ({ orderItemId: row.line.id as string, quantity: Math.floor(row.control.value) }));
    if (lines.length === 0) {
      this.failure.set('Invoice at least one unit from the order lines.');
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.invoices.create(this.orderControl.value, this.numberControl.value.trim(), lines).subscribe({
      next: (invoice) => this.dialog.close({ id: invoice.id ?? null }),
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }
}

export interface CreateInvoiceDialogResult {
  id: string | null;
}
