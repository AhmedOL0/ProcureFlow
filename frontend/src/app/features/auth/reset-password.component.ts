import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { Eye, EyeOff } from '../../shared/icons';
import { parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';

/**
 * Redeems a reset link (?token=…). Tokens are single-use and short-lived:
 * unknown links 404, spent or expired ones 410 — each with words that say
 * what to do next (request a fresh link). Success routes to login; the
 * flow never signs anyone in by itself.
 */
@Component({
  selector: 'app-reset-password',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ResetPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly icons = { show: Eye, hide: EyeOff };
  protected readonly token = signal(this.route.snapshot.queryParamMap.get('token') ?? '');
  protected readonly showPassword = signal(false);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly failureCode = signal<string | null>(null);

  readonly form = new FormGroup({
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const confirm = this.form.controls.confirmPassword;
      if (confirm.hasError('mismatch') && this.form.controls.newPassword.value === confirm.value) {
        confirm.setErrors(null);
      }
    });
  }

  submit(): void {
    if (!this.token()) {
      return;
    }
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.newPassword !== value.confirmPassword) {
      this.form.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.auth.resetPassword(this.token(), value.newPassword).subscribe({
      next: () => void this.router.navigate(['/login']),
      error: (error: unknown) => {
        this.busy.set(false);
        const parsed = parseApiFailure(error);
        this.failure.set(userMessageFor(parsed));
        this.failureCode.set(parsed.code);
      },
    });
  }

  protected expiredOrUsed(): boolean {
    return this.failureCode() === 'TOKEN_SPENT';
  }
}
