import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { Eye, EyeOff } from '../../shared/icons';

export type RegisterMode = 'create' | 'join';

/**
 * Workspace registration in two explicit modes: a new slug plus tenant
 * name creates a workspace (caller becomes admin); an existing slug joins
 * it with a live invite code. The toggle keeps the two paths from
 * contradicting each other; backend errors flip to the mode that can
 * actually fix them.
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatChipsModule, MatProgressSpinnerModule, LucideAngularModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  protected readonly lang = inject(LanguageService);

  protected readonly icons = { show: Eye, hide: EyeOff };
  readonly mode = signal<RegisterMode>('create');

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12), Validators.maxLength(72)],
    }),
    tenantSlug: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    tenantName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    inviteToken: new FormControl('', { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly showPassword = signal(false);
  readonly failure = signal<string | null>(null);

  setMode(mode: RegisterMode): void {
    if (mode !== 'create' && mode !== 'join') {
      return;
    }
    this.mode.set(mode);
    this.failure.set(null);
    if (mode === 'create') {
      this.form.controls.tenantName.setValidators(Validators.required);
      this.form.controls.inviteToken.clearValidators();
    } else {
      this.form.controls.tenantName.clearValidators();
      this.form.controls.inviteToken.setValidators(Validators.required);
    }
    this.form.controls.tenantName.updateValueAndValidity();
    this.form.controls.inviteToken.updateValueAndValidity();
  }

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const { email, password, tenantSlug, tenantName, inviteToken } = this.form.getRawValue();
    const creating = this.mode() === 'create';
    this.auth
      .register(
        email,
        password,
        tenantSlug.trim(),
        creating ? tenantName : '',
        creating ? '' : inviteToken,
      )
      .subscribe({
        error: (error: unknown) => {
          this.busy.set(false);
          const code = this.auth.apiErrorCode(error);
          if (code === 'EMAIL_IN_USE') {
            this.failure.set(this.lang.t('auth.emailInUse'));
          } else if (code === 'INVITE_REQUIRED') {
            this.setMode('join');
            this.failure.set(this.lang.t('auth.workspaceExists'));
          } else if (code === 'TENANT_NAME_REQUIRED') {
            this.setMode('create');
            this.failure.set(this.lang.t('auth.nameRequiredCreate'));
          } else if (code === 'WEAK_PASSWORD') {
            this.failure.set(this.lang.t('auth.passwordMin'));
          } else if (code === 'PASSWORD_TOO_LONG') {
            this.failure.set(this.lang.t('auth.passwordLong'));
          } else {
            this.failure.set(this.lang.t('auth.registerFailed'));
          }
        },
      });
  }
}
