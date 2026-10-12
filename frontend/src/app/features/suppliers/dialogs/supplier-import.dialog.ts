import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SupplierImportResult, SuppliersService } from '../suppliers.service';
import { saveBlob } from '../../../shared/utils/download';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';

const MAX_BYTES = 5 * 1024 * 1024;
const TEMPLATE =
  'name,email,phone,tax_id,address,status,categories\n' +
  'Acme Parts,orders@acme.test,0600000001,DE-123,12 Main St,active,Hardware;Office\n';

/**
 * Bulk supplier onboarding from .xlsx/.csv. Client checks (extension, 5 MB)
 * run before anything crosses the wire; the backend validates every row and
 * imports all-or-nothing, so a failure lists row errors and imports zero
 * suppliers. Closing after a success reloads the directory.
 */
@Component({
  selector: 'app-supplier-import-dialog',
  imports: [MatButtonModule, MatDialogModule, MatProgressSpinnerModule],
  templateUrl: './supplier-import.dialog.html',
  styleUrl: './supplier-import.dialog.scss',
})
export class SupplierImportDialogComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly dialog = inject(MatDialogRef<SupplierImportDialogComponent, boolean>);

  protected readonly fileName = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly rowErrors = signal<RowErrorView[]>([]);
  protected readonly created = signal<number | null>(null);
  private pending: File | null = null;

  pick(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    this.failure.set(null);
    this.rowErrors.set([]);
    this.created.set(null);
    if (!file) {
      return;
    }
    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.xlsx') && !lower.endsWith('.csv')) {
      this.failure.set('Only .xlsx and .csv files can be imported.');
      this.pending = null;
      this.fileName.set(null);
      return;
    }
    if (file.size > MAX_BYTES) {
      this.failure.set('The file exceeds the 5 MB import limit.');
      this.pending = null;
      this.fileName.set(null);
      return;
    }
    this.pending = file;
    this.fileName.set(file.name);
  }

  template(): void {
    saveBlob(new Blob([TEMPLATE], { type: 'text/csv;charset=utf-8' }), 'supplier-import-template.csv');
  }

  upload(): void {
    if (!this.pending || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.rowErrors.set([]);
    this.suppliers.importFile(this.pending).subscribe({
      next: (result: SupplierImportResult) => {
        this.busy.set(false);
        this.created.set(result.created ?? 0);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const failure: ApiFailure = parseApiFailure(error);
        this.failure.set(userMessageFor(failure));
        this.rowErrors.set(toRowErrors(failure.details));
      },
    });
  }

  close(): void {
    this.dialog.close(this.created() !== null);
  }
}

interface RowErrorView {
  row: number;
  field: string;
  message: string;
}

/** Flattened `row.N.field` details back into a sorted error list. */
function toRowErrors(details: Record<string, string>): RowErrorView[] {
  const errors: RowErrorView[] = [];
  for (const [key, message] of Object.entries(details)) {
    const match = /^row\.(\d+)\.(.+)$/.exec(key);
    if (match) {
      errors.push({ row: Number(match[1]), field: match[2], message });
    }
  }
  return errors.sort((left, right) => left.row - right.row);
}
