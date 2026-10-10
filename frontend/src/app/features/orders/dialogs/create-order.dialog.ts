import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { OrderService } from '../orders.service';
import { ProcurementService, PurchaseRequest } from '../../procurement/procurement.service';
import { Supplier, SuppliersService } from '../../suppliers/suppliers.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

/**
 * Start an order from an approved request. Only APPROVED requests are
 * offered — approving never creates the order by itself.
 */
@Component({
  selector: 'app-create-order-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './create-order.dialog.html',
})
export class CreateOrderDialogComponent {
  private readonly orders = inject(OrderService);
  private readonly requests = inject(ProcurementService);
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<CreateOrderDialogComponent, CreateOrderDialogResult>);

  readonly requestControl = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly supplierControl = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly approved = signal<PurchaseRequest[]>([]);
  readonly vendors = signal<Supplier[]>([]);
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  constructor() {
    this.requests.list('APPROVED').subscribe({
      next: (rows) => this.approved.set(rows ?? []),
      error: (error: unknown) => this.failure.set(userMessageFor(parseApiFailure(error))),
    });
    this.suppliers.search(null, null).subscribe({
      next: (rows) => this.vendors.set(rows ?? []),
      error: () => undefined,
    });
  }

  save(): void {
    if (!this.requestControl.value || !this.supplierControl.value || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.orders.create(this.requestControl.value, this.supplierControl.value).subscribe({
      next: (order) => this.dialog.close({ id: order.id ?? null }),
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }
}

export interface CreateOrderDialogResult {
  id: string | null;
}
