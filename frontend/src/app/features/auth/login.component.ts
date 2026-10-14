import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { Eye, EyeOff } from '../../shared/icons';

/**
 * Workspace sign-in. A 409 LOGIN_AMBIGUOUS (same email in several
 * workspaces) reveals the workspace-slug field instead of failing.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule, LucideAngularModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  protected readonly lang = inject(LanguageService);

  protected readonly icons = { show: Eye, hide: EyeOff };

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    tenantSlug: new FormControl('', { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly needsTenant = signal(false);
  readonly showPassword = signal(false);
  readonly failure = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password, tenantSlug } = this.form.getRawValue();
    if (this.needsTenant() && !tenantSlug.trim()) {
      this.form.controls.tenantSlug.setValidators(Validators.required);
      this.form.controls.tenantSlug.updateValueAndValidity();
      this.form.controls.tenantSlug.markAsTouched();
      this.failure.set(this.lang.t('auth.slugContinue'));
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.auth.login(email, password, tenantSlug.trim() || undefined).subscribe({
      error: (error: unknown) => {
        this.busy.set(false);
        if (this.auth.apiErrorCode(error) === 'LOGIN_AMBIGUOUS') {
          this.needsTenant.set(true);
          this.failure.set(this.lang.t('auth.ambiguousLogin'));
          return;
        }
        this.failure.set(this.lang.t('auth.invalidCredentials'));
      },
    });
  }
}
