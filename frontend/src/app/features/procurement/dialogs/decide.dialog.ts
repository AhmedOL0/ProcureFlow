import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { LanguageService } from '../../../core/i18n/language.service';

/** Approve/reject with an optional comment. The verdict is immutable. */
@Component({
  selector: 'app-decide-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.approved ? lang.t('req.approveTitle') : lang.t('req.rejectTitle') }}</h2>
    <mat-dialog-content>
      <p class="decide-sub">{{ lang.tx('req.verdictNote', { title: data.title }) }}</p>
      <mat-form-field appearance="outline" class="decide-field">
        <mat-label>{{ lang.t('req.commentLabel') }}</mat-label>
        <textarea matInput [formControl]="comment" rows="2"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ lang.t('dialog.cancel') }}</button>
      <button
        mat-flat-button
        [color]="data.approved ? 'primary' : 'warn'"
        (click)="confirm()"
      >
        {{ data.approved ? lang.t('req.approve') : lang.t('req.reject') }}
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
  protected readonly lang = inject(LanguageService);
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
