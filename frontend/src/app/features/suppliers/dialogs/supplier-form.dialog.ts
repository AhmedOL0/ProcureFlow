import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { Supplier, SuppliersService } from '../suppliers.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

export interface SupplierFormData {
  mode: 'create' | 'edit';
  supplier?: Supplier;
}

/** Create/edit supplier. Blank optional fields stay untouched on edit. */
@Component({
  selector: 'app-supplier-form-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './supplier-form.dialog.html',
})
export class SupplierFormDialogComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<SupplierFormDialogComponent, boolean>);
  protected readonly data = inject<SupplierFormData>(MAT_DIALOG_DATA);

  readonly form = new FormGroup({
    name: new FormControl(this.data.supplier?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    taxId: new FormControl(this.data.supplier?.taxId ?? '', { nonNullable: true }),
    email: new FormControl(this.data.supplier?.email ?? '', {
      nonNullable: true,
      validators: [Validators.email],
    }),
    phone: new FormControl(this.data.supplier?.phone ?? '', { nonNullable: true }),
    address: new FormControl(this.data.supplier?.address ?? '', { nonNullable: true }),
    status: new FormControl(this.data.supplier?.status ?? 'ACTIVE', { nonNullable: true }),
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
    // Edits apply as typed (blank clears); creates omit blanks.
    const trim = (input: string): string | undefined => (input.trim() === '' ? undefined : input.trim());
    const request = this.isCreate() || !this.data.supplier?.id
      ? this.suppliers.create({
          name: value.name.trim(),
          taxId: trim(value.taxId),
          email: trim(value.email),
          phone: trim(value.phone),
          address: trim(value.address),
        })
      : this.suppliers.update(this.data.supplier.id, {
          name: value.name.trim(),
          taxId: value.taxId.trim(),
          email: value.email.trim(),
          phone: value.phone.trim(),
          address: value.address.trim(),
          status: value.status as 'ACTIVE' | 'INACTIVE' | 'SUSPENDED',
        });
    request.subscribe({
      next: () => this.dialog.close(true),
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }
}

export type SupplierFormResult = boolean;
