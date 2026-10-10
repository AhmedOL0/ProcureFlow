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
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Building2, Plus, Trash2, Users } from '../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { PromptDialogComponent } from '../../shared/components/prompt-dialog/prompt-dialog.component';
import { Department, Membership, OrganizationService, Tenant, WorkspaceUser } from './organization.service';

/**
 * Workspace administration: tenant rename, departments, memberships and
 * users. Every section hides its write actions unless the caller holds the
 * matching authority — the backend enforces all of it regardless. Roles can
 * only be assigned at user creation (the contract exposes no role-update
 * endpoint), so this screen states that instead of faking it.
 */
@Component({
  selector: 'app-admin',
  imports: [
    ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatProgressSpinnerModule, LucideAngularModule, PageHeaderComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent {
  private readonly org = inject(OrganizationService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);

  protected readonly icons = { building: Building2, plus: Plus, trash: Trash2, users: Users };
  protected readonly canManageTenant = this.auth.hasAuthority('tenant:manage');
  protected readonly canManageDepartments = this.auth.hasAuthority('department:manage');
  protected readonly canManageUsers = this.auth.hasAuthority('user:manage');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly departments = signal<Department[]>([]);
  protected readonly memberships = signal<Membership[]>([]);
  protected readonly users = signal<WorkspaceUser[]>([]);
  protected readonly notice = signal<string | null>(null);

  readonly tenantName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly departmentName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly memberUser = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  readonly memberDepartment = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  readonly newEmail = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.email],
  });
  readonly newPassword = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(12)],
  });
  readonly newRoles = new FormControl<string[]>([], { nonNullable: true });
  readonly roleOptions = ['TENANT_ADMIN', 'OFFICER', 'MEMBER'];

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.notice.set(null);
    this.org.tenant().subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.tenantName.setValue(tenant.name ?? '');
        this.loading.set(false);
        this.loadSections();
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  private loadSections(): void {
    this.org.departments().subscribe({
      next: (rows) => this.departments.set(rows ?? []),
      error: () => undefined,
    });
    this.org.memberships().subscribe({
      next: (rows) => this.memberships.set(rows ?? []),
      error: () => undefined,
    });
    if (this.canManageUsers) {
      this.org.users().subscribe({
        next: (rows) => this.users.set(rows ?? []),
        error: (error: unknown) => this.failure.set(parseApiFailure(error)),
      });
    }
  }

  protected userEmail(userId: string | null | undefined): string {
    if (!userId) {
      return '?';
    }
    return this.users().find((u) => u.id === userId)?.email ?? userId.slice(0, 8);
  }

  protected departmentNameOf(departmentId: string | null | undefined): string {
    if (!departmentId) {
      return '?';
    }
    return this.departments().find((d) => d.id === departmentId)?.name ?? departmentId.slice(0, 8);
  }

  renameTenant(): void {
    const name = this.tenantName.value.trim();
    if (!name) {
      return;
    }
    this.org.renameTenant(name).subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.notice.set('Workspace renamed.');
      },
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  createDepartment(): void {
    const name = this.departmentName.value.trim();
    if (!name) {
      return;
    }
    this.org.createDepartment(name).subscribe({
      next: () => {
        this.departmentName.reset('');
        this.notice.set('Department created.');
        this.reload();
      },
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  renameDepartment(department: Department): void {
    if (!department.id) {
      return;
    }
    const dialog = this.dialogs.open(PromptDialogComponent, {
      width: '400px',
      data: { title: 'Rename department', label: 'Department name', initial: department.name ?? '' },
    });
    dialog.afterClosed().subscribe((name) => {
      if (typeof name === 'string' && name.trim() && department.id) {
        this.org.renameDepartment(department.id, name.trim()).subscribe({
          next: () => this.reload(),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }

  deleteDepartment(department: Department): void {
    if (!department.id) {
      return;
    }
    const dialog = this.dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: `Delete ${department.name ?? 'department'}?`,
        message: 'Only empty departments can be deleted — the backend refuses the rest.',
        confirmLabel: 'Delete department',
      },
    });
    dialog.afterClosed().subscribe((confirmed) => {
      if (confirmed === true && department.id) {
        this.org.deleteDepartment(department.id).subscribe({
          next: () => this.reload(),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }

  addMembership(): void {
    if (!this.memberUser.value || !this.memberDepartment.value) {
      return;
    }
    this.org.addMembership(this.memberUser.value, this.memberDepartment.value).subscribe({
      next: () => {
        this.notice.set('Membership added.');
        this.reload();
      },
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  removeMembership(membership: Membership): void {
    if (!membership.id) {
      return;
    }
    this.org.removeMembership(membership.id).subscribe({
      next: () => this.reload(),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  createUser(): void {
    if (this.newEmail.invalid || this.newPassword.invalid || this.newRoles.value.length === 0) {
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
}
