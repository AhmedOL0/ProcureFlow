import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { SupplierCategory, SuppliersService } from '../suppliers.service';
import { userMessageFor, parseApiFailure } from '../../../shared/utils/api-errors';

export interface CategoriesDialogData {
  supplierId: string;
  assigned: SupplierCategory[];
}

/** Replace the supplier's category set; create catalog entries inline. */
@Component({
  selector: 'app-categories-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './categories.dialog.html',
  styleUrl: './dialogs.scss',
})
export class CategoriesDialogComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<CategoriesDialogComponent, boolean>);
  protected readonly data = inject<CategoriesDialogData>(MAT_DIALOG_DATA);

  readonly selection = new FormControl<string[]>(this.data.assigned.map((c) => c.id ?? ''), {
    nonNullable: true,
  });
  readonly catalog = signal<SupplierCategory[]>([]);
  readonly newName = new FormControl('', { nonNullable: true });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  constructor() {
    this.suppliers.catalog().subscribe({
      next: (rows) => this.catalog.set(rows ?? []),
      error: (error: unknown) => this.failure.set(userMessageFor(parseApiFailure(error))),
    });
  }

  createCategory(): void {
    const name = this.newName.value.trim();
    if (!name || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.suppliers.createCategory(name).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.newName.reset('');
        if (created.id) {
          this.catalog.update((rows) => [...rows, created]);
          this.selection.setValue([...this.selection.value, created.id]);
        }
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }

  save(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.suppliers.assignCategories(this.data.supplierId, this.selection.value).subscribe({
      next: () => this.dialog.close(true),
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(userMessageFor(parseApiFailure(error)));
      },
    });
  }
}
