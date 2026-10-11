import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Eye, EyeOff } from '../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';

/**
 * Account settings: profile names and self-service password change.
 * Email, roles and status are not editable here by contract design (email
 * moves through a verified workflow that does not exist yet; roles assign
 * at user creation). Changing the password ends every session, so success
 * routes to the login screen.
 */
@Component({
  selector: 'app-settings',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    ErrorStateComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly icons = { show: Eye, hide: EyeOff };
  protected readonly user = this.auth.currentUser;

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly showCurrent = signal(false);
  protected readonly showNew = signal(false);
  protected readonly savingProfile = signal(false);
  protected readonly changingPassword = signal(false);

  readonly profileForm = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
  });
  readonly passwordForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.reload();
    // Clear a stale mismatch as soon as the two fields agree again.
    this.passwordForm.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const confirm = this.passwordForm.controls.confirmPassword;
      if (
        confirm.hasError('mismatch') &&
        this.passwordForm.controls.newPassword.value === confirm.value
      ) {
        confirm.setErrors(null);
      }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.auth.refreshProfile().subscribe({
      next: (user) => {
        this.profileForm.setValue({
          firstName: user.firstName ?? '',
          lastName: user.lastName ?? '',
        });
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  saveProfile(): void {
    if (this.profileForm.invalid || this.savingProfile()) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.savingProfile.set(true);
    this.notice.set(null);
    const value = this.profileForm.getRawValue();
    this.auth
      .updateOwnProfile(value.firstName.trim() || null, value.lastName.trim() || null)
      .subscribe({
        next: () => {
          this.savingProfile.set(false);
          this.notice.set('Profile saved.');
        },
        error: (error: unknown) => {
          this.savingProfile.set(false);
          this.failure.set(parseApiFailure(error));
        },
      });
  }

  changePassword(): void {
    if (this.passwordForm.invalid || this.changingPassword()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const value = this.passwordForm.getRawValue();
    if (value.newPassword !== value.confirmPassword) {
      this.passwordForm.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }
    this.changingPassword.set(true);
    this.auth.changePassword(value.currentPassword, value.newPassword).subscribe({
      next: () => {
        this.auth.logout(false);
        void this.router.navigate(['/login']);
      },
      error: (error: unknown) => {
        this.changingPassword.set(false);
        this.failure.set(parseApiFailure(error));
      },
    });
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }
}
