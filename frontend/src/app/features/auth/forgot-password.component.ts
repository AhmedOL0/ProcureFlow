import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Forgot password: email plus optional workspace slug (mirrors the login
 * disambiguation — an address alone is ambiguous across tenants). The
 * answer is always the same generic message, whether or not the account
 * exists, so the screen states that outright.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatProgressSpinnerModule,
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthService);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    tenantSlug: new FormControl('', { nonNullable: true }),
  });

  protected readonly busy = signal(false);
  protected readonly sentMessage = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);

  submit(): void {
    if (this.form.controls.email.invalid || this.busy()) {
      this.form.controls.email.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const value = this.form.getRawValue();
    this.auth.forgotPassword(value.email.trim(), value.tenantSlug).subscribe({
      next: (response) => {
        this.busy.set(false);
        this.sentMessage.set(response.message ?? 'If an account exists for that address, a reset link is on its way.');
      },
      error: () => {
        this.busy.set(false);
        this.failure.set('The request could not be sent. Check your connection and try again.');
      },
    });
  }
}
