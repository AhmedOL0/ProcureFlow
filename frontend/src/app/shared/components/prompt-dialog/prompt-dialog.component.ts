import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface PromptDialogData {
  title: string;
  message?: string;
  label: string;
  initial?: string;
  confirmLabel?: string;
  required?: boolean;
  /** Masked input plus minimum length, for secrets like passwords. */
  secret?: boolean;
  minLength?: number;
}

/** Single labeled text input with confirm/cancel. Labeled, focusable, testable. */
@Component({
  selector: 'pf-prompt-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.message) {
        <p class="pf-prompt__message">{{ data.message }}</p>
      }
      <mat-form-field appearance="outline" class="pf-prompt__field">
        <mat-label>{{ data.label }}</mat-label>
        <input
          matInput
          [formControl]="value"
          [type]="data.secret ? 'password' : 'text'"
          cdkFocusInitial
        />
        @if (value.hasError('required') && value.touched) {
          <mat-error>A value is required.</mat-error>
        }
        @if (value.hasError('minlength') && value.touched) {
          <mat-error>At least {{ data.minLength }} characters.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="confirm()" [disabled]="value.invalid">
        {{ data.confirmLabel ?? 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .pf-prompt__message {
        font: var(--pf-body-md);
        color: var(--pf-slate);
        margin: 0 0 8px;
      }
      .pf-prompt__field {
        width: 100%;
        min-width: min(360px, 70vw);
      }
    `,
  ],
})
export class PromptDialogComponent {
  private readonly dialog = inject(MatDialogRef<PromptDialogComponent, string | null>);
  protected readonly data = inject<PromptDialogData>(MAT_DIALOG_DATA);
  readonly value = new FormControl(this.data.initial ?? '', {
    nonNullable: true,
    validators: [
      ...(this.data.required === false ? [] : [Validators.required]),
      ...(this.data.minLength ? [Validators.minLength(this.data.minLength)] : []),
    ],
  });

  confirm(): void {
    if (this.value.invalid) {
      this.value.markAsTouched();
      return;
    }
    this.dialog.close(this.value.value.trim() || null);
  }
}
