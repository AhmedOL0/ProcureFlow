import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
}

/** Destructive confirmation with an explicit, specific question. */
@Component({
  selector: 'pf-confirm-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p class="pf-confirm__message">{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true" cdkFocusInitial>
        {{ data.confirmLabel ?? 'Delete' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .pf-confirm__message {
        font: var(--pf-body-md);
        color: var(--pf-slate);
        margin: 0;
      }
    `,
  ],
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
