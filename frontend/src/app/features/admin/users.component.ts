import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PromptDialogComponent } from '../../shared/components/prompt-dialog/prompt-dialog.component';
import { Users } from '../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';
import { OrganizationService, WorkspaceUser } from './organization.service';

/**
 * Users & roles: workspace accounts and the roles they were created with.
 * Roles assign at creation only — the contract exposes no role-update
 * endpoint, so this screen states that instead of faking it. The catalog
 * mirrors the backend defaults: TENANT_ADMIN, APPROVER, FINANCE, OFFICER,
 * AUDITOR, MEMBER (unknown names answer 400 UNKNOWN_ROLE).
 */
@Component({
  selector: 'app-users',
  imports: [
    ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatProgressSpinnerModule, LucideAngularModule,
    PageHeaderComponent, ErrorStateComponent,
  ],
  templateUrl: './users.component.html',
  styleUrl: './admin-cards.scss',
})
export class UsersComponent {
  private readonly org = inject(OrganizationService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);

  protected readonly icons = { users: Users };
  protected readonly canManageUsers = this.auth.hasAuthority('user:manage');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly users = signal<WorkspaceUser[]>([]);
  protected readonly notice = signal<string | null>(null);

  readonly newEmail = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.email],
  });
  readonly newPassword = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(12)],
  });
  readonly newRoles = new FormControl<string[]>([], { nonNullable: true });
  readonly roleOptions = ['TENANT_ADMIN', 'APPROVER', 'FINANCE', 'OFFICER', 'AUDITOR', 'MEMBER'];

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.notice.set(null);
    this.org.users().subscribe({
      next: (rows) => {
        this.users.set(rows ?? []);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  createUser(): void {    if (this.newEmail.invalid || this.newPassword.invalid || this.newRoles.value.length === 0) {
      this.newEmail.markAsTouched();
      this.newPassword.markAsTouched();
      return;
    }
    this.org
      .createUser({
        email: this.newEmail.value.trim(),
        password: this.newPassword.value,
        roleNames: this.newRoles.value,
      })
      .subscribe({
        next: () => {
          this.newEmail.reset('');
          this.newPassword.reset('');
          this.newRoles.reset([]);
          this.notice.set('User created with the selected roles.');
          this.reload();
        },
        error: (error: unknown) => this.failure.set(parseApiFailure(error)),
      });
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }

  resetPassword(user: WorkspaceUser): void {
    if (!user.id) {
      return;
    }
    const dialog = this.dialogs.open(PromptDialogComponent, {
      width: '400px',
      data: {
        title: `Reset password for ${user.email ?? 'user'}?`,
        message: 'The new password applies immediately and ends all of their sessions.',
        label: 'New password (12+ characters)',
        confirmLabel: 'Reset password',
        secret: true,
        minLength: 12,
      },
    });
    dialog.afterClosed().subscribe((password) => {
      if (typeof password === 'string' && password && user.id) {
        this.org.resetPassword(user.id, password).subscribe({
          next: () => this.notice.set(`Password reset for ${user.email ?? 'user'}.`),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }
}
