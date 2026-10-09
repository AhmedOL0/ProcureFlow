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
 * Workspace registration: a new slug plus tenant name creates a workspace
 * (caller becomes admin); an existing slug joins it as a member.
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
    tenantSlug: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    tenantName: new FormControl('', { nonNullable: true }),
  });
  readonly busy = signal(false);
  readonly failure = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const { email, password, tenantSlug, tenantName } = this.form.getRawValue();
    this.auth.register(email, password, tenantSlug.trim(), tenantName).subscribe({
      error: (error: unknown) => {
        this.busy.set(false);
        const code = this.auth.apiErrorCode(error);
        if (code === 'EMAIL_IN_USE') {
          this.failure.set('This email is already registered in this workspace — try signing in.');
        } else if (code === 'TENANT_NAME_REQUIRED') {
          this.failure.set('A workspace name is required to create a new workspace.');
        } else if (code === 'WEAK_PASSWORD') {
          this.failure.set('Password must be at least 12 characters.');
        } else {
          this.failure.set('Registration failed. Check the workspace slug and try again.');
        }
      },
    });
  }
}
