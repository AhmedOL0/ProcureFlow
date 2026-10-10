import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SupplierContact, SuppliersService } from '../suppliers.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

export interface ContactDialogData {
  supplierId: string;
  contact?: SupplierContact;
}

/**
 * Add/edit contact. Marking primary demotes the previous primary
 * server-side; the file reload shows the outcome.
 */
@Component({
  selector: 'app-contact-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatProgressSpinnerModule],
  templateUrl: './contact.dialog.html',
})
export class ContactDialogComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<ContactDialogComponent, boolean>);
  protected readonly data = inject<ContactDialogData>(MAT_DIALOG_DATA);

  readonly form = new FormGroup({
    name: new FormControl(this.data.contact?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    title: new FormControl(this.data.contact?.title ?? '', { nonNullable: true }),
    email: new FormControl(this.data.contact?.email ?? '', {
      nonNullable: true,
      validators: [Validators.email],
    }),
    phone: new FormControl(this.data.contact?.phone ?? '', { nonNullable: true }),
    primary: new FormControl(this.data.contact?.primary ?? false, { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  protected isCreate(): boolean {
    return !this.data.contact?.id;
  }

  save(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const value = this.form.getRawValue();
    const trim = (input: string): string | undefined => (input.trim() === '' ? undefined : input.trim());
    const request = this.isCreate()
      ? this.suppliers.addContact(this.data.supplierId, {
          name: value.name.trim(),
          title: trim(value.title),
          email: trim(value.email),
          phone: trim(value.phone),
          primary: value.primary,
        })
      : this.suppliers.updateContact(this.data.contact?.id ?? '', {
          name: value.name.trim(),
          title: value.title.trim(),
          email: value.email.trim(),
          phone: value.phone.trim(),
          primary: value.primary,
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

export type ContactDialogResult = boolean;
