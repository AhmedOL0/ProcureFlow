import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

/** Approve/reject with an optional comment. The verdict is immutable. */
@Component({
  selector: 'app-decide-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.approved ? 'Approve request' : 'Reject request' }}</h2>
    <mat-dialog-content>
      <p class="decide-sub">“{{ data.title }}” — this verdict cannot be changed afterwards.</p>
      <mat-form-field appearance="outline" class="decide-field">
        <mat-label>Comment (optional)</mat-label>
        <textarea matInput [formControl]="comment" rows="2"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        [color]="data.approved ? 'primary' : 'warn'"
        (click)="confirm()"
      >
        {{ data.approved ? 'Approve' : 'Reject' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .decide-sub {
        font: var(--pf-body-md);
        color: var(--pf-slate);
        margin: 0 0 8px;
      }
      .decide-field {
        width: 100%;
        min-width: min(380px, 70vw);
      }
    `,
  ],
})
export class DecideDialogComponent {
  private readonly dialog = inject(MatDialogRef<DecideDialogComponent, DecideDialogResult>);
  protected readonly data = inject<DecideDialogData>(MAT_DIALOG_DATA);
  readonly comment = new FormControl('', { nonNullable: true });
  readonly busy = signal(false);

  confirm(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.dialog.close({ comment: this.comment.value });
  }
}

export interface DecideDialogData {
  approved: boolean;
  title: string;
}

export interface DecideDialogResult {
  comment: string;
}
