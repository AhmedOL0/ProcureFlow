import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ProcurementService, RequestItem } from '../procurement.service';
import { Supplier, SuppliersService } from '../../suppliers/suppliers.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

export interface RequestItemDialogData {
  requestId: string;
  item?: RequestItem;
}

/** Add/edit one request line, optionally linked to a workspace supplier. */
@Component({
  selector: 'app-request-item-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './request-item.dialog.html',
})
export class RequestItemDialogComponent {
  private readonly requests = inject(ProcurementService);
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<RequestItemDialogComponent, boolean>);
  protected readonly data = inject<RequestItemDialogData>(MAT_DIALOG_DATA);

  readonly form = new FormGroup({
    description: new FormControl(this.data.item?.description ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(500)],
    }),
    category: new FormControl(this.data.item?.category ?? '', { nonNullable: true }),
    quantity: new FormControl<number>(this.data.item?.quantity ?? 1, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1)],
    }),
    unitPriceMinor: new FormControl<number>(this.data.item?.unitPriceMinor ?? 0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
    supplierId: new FormControl<string | null>(this.data.item?.supplierId ?? null),
  });
  readonly options = signal<Supplier[]>([]);
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  constructor() {
    this.suppliers.search(null, null).subscribe({
      next: (rows) => this.options.set(rows ?? []),
      error: () => undefined,
    });
  }

  protected isCreate(): boolean {
    return !this.data.item?.id;
  }

  save(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const value = this.form.getRawValue();
    const body = {
      description: value.description.trim(),
      category: value.category.trim() || undefined,
      quantity: value.quantity,
      unitPriceMinor: Math.round(value.unitPriceMinor),
    };
    const withSupplier = value.supplierId ? { ...body, supplierId: value.supplierId } : body;
    const request = this.isCreate()
      ? this.requests.addItem(this.data.requestId, withSupplier)
      : this.requests.updateItem(this.data.item?.id ?? '', withSupplier);
    request.subscribe({
      next: () => this.dialog.close(true),
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }
}

export type RequestItemDialogResult = boolean;
