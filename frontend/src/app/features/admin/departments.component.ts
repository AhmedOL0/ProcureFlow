import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PromptDialogComponent } from '../../shared/components/prompt-dialog/prompt-dialog.component';
import { Building2 } from '../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';
import { Department, Membership, OrganizationService, WorkspaceUser } from './organization.service';

/**
 * Departments & assignments: cost-center structure plus who sits where.
 * Only empty departments can be deleted — the backend refuses the rest
 * with an explicit conflict. The member picker lists users only when the
 * caller may manage users; otherwise assignments resolve to id prefixes.
 */
@Component({
  selector: 'app-departments',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './departments.component.html',
})
export class DepartmentsComponent {
  private readonly org = inject(OrganizationService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);

  protected readonly icons = { building: Building2 };
  protected readonly canManageDepartments = this.auth.hasAuthority('department:manage');
  private readonly canSeeUsers = this.auth.hasAuthority('user:manage');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly departments = signal<Department[]>([]);
  protected readonly memberships = signal<Membership[]>([]);
  protected readonly users = signal<WorkspaceUser[]>([]);
  protected readonly notice = signal<string | null>(null);
  protected readonly deptPage = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });
  protected readonly memberPage = signal<PageEvent>({ pageIndex: 0, pageSize: 10, length: 0 });

  readonly departmentName = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly memberUser = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly memberDepartment = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.notice.set(null);
    const dept = this.deptPage();
    const member = this.memberPage();
    forkJoin({
      departments: this.org.departments('', dept.pageIndex, dept.pageSize),
      memberships: this.org.memberships(member.pageIndex, member.pageSize),
      // The member picker resolves names only for user managers; the list
      // below falls back to id prefixes for everyone else. First user page
      // feeds the picker; the users screen owns the full directory.
      users: this.canSeeUsers ? this.org.users('', 0, 100) : of({ rows: [], total: 0 }),
    }).subscribe({
      next: ({ departments, memberships, users }) => {
        this.departments.set(departments.rows);
        this.deptPage.update((p) => ({ ...p, length: departments.total }));
        this.memberships.set(memberships.rows);
        this.memberPage.update((p) => ({ ...p, length: memberships.total }));
        this.users.set(users.rows);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  onDeptPage(page: PageEvent): void {
    this.deptPage.set(page);
    this.reload();
  }

  onMemberPage(page: PageEvent): void {
    this.memberPage.set(page);
    this.reload();
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
      data: {
        title: 'Rename department',
        label: 'Department name',
        initial: department.name ?? '',
      },
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
    const dialog = this.dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
      ConfirmDialogComponent,
      {
        width: '400px',
        data: {
          title: `Delete ${department.name ?? 'department'}?`,
          message: 'Only empty departments can be deleted — the backend refuses the rest.',
          confirmLabel: 'Delete department',
        },
      },
    );
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
    const dialog = this.dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
      ConfirmDialogComponent,
      {
        width: '400px',
        data: {
          title: `Remove ${this.userEmail(membership.userId)} from ${this.departmentNameOf(membership.departmentId)}?`,
          message:
            'The person keeps their workspace account — only the department assignment ends.',
          confirmLabel: 'Remove assignment',
        },
      },
    );
    dialog.afterClosed().subscribe((confirmed) => {
      if (confirmed === true && membership.id) {
        this.org.removeMembership(membership.id).subscribe({
          next: () => this.reload(),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }
}
