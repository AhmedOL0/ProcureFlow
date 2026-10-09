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
 * Workspace sign-in. A 409 LOGIN_AMBIGUOUS (same email in several
 * workspaces) reveals the workspace-slug field instead of failing.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    tenantSlug: new FormControl('', { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly needsTenant = signal(false);
  readonly failure = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const { email, password, tenantSlug } = this.form.getRawValue();
    this.auth.login(email, password, tenantSlug.trim() || undefined).subscribe({
      error: (error: unknown) => {
        this.busy.set(false);
        if (this.auth.apiErrorCode(error) === 'LOGIN_AMBIGUOUS') {
          this.needsTenant.set(true);
          this.failure.set('This email lives in several workspaces — enter your workspace slug.');
          return;
        }
        this.failure.set('Invalid email or password.');
      },
    });
  }
}
