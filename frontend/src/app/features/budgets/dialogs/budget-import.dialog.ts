import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { BudgetImportResult, BudgetsService } from '../budgets.service';
import { saveBlob } from '../../../shared/utils/download';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';

const MAX_BYTES = 5 * 1024 * 1024;
const TEMPLATE =
  'name,period,amount,currency\n' + 'Operations,2026-10,50000.00,MAD\n';

/**
 * Bulk pot onboarding from .xlsx/.csv. Client checks (extension, 5 MB)
 * run before anything crosses the wire; the backend validates every row and
 * opens pots all-or-nothing, so a failure lists row errors and opens zero
 * pots. Amounts are major units with up to 2 decimals. Closing after a
 * success reloads the directory.
 */
@Component({
  selector: 'app-budget-import-dialog',
  imports: [MatButtonModule, MatDialogModule, MatProgressSpinnerModule],
  templateUrl: './budget-import.dialog.html',
  styleUrl: './budget-import.dialog.scss',
})
export class BudgetImportDialogComponent {
  private readonly budgets = inject(BudgetsService);
  private readonly dialog = inject(MatDialogRef<BudgetImportDialogComponent, boolean>);

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
    saveBlob(new Blob([TEMPLATE], { type: 'text/csv;charset=utf-8' }), 'budget-import-template.csv');
  }

  upload(): void {
    if (!this.pending || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.rowErrors.set([]);
    this.budgets.importFile(this.pending).subscribe({
      next: (result: BudgetImportResult) => {
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
