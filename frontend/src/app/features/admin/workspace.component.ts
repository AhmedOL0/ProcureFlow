import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../core/auth/auth.service';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';
import { OrganizationService, Tenant } from './organization.service';

/** Workspace settings: tenant rename. Departments, users and lanes live on sibling screens. */
@Component({
  selector: 'app-workspace',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    ErrorStateComponent,
  ],
  templateUrl: './workspace.component.html',
})
export class WorkspaceComponent {
  private readonly org = inject(OrganizationService);
  private readonly auth = inject(AuthService);

  protected readonly canManageTenant = this.auth.hasAuthority('tenant:manage');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly notice = signal<string | null>(null);

  readonly tenantName = new FormControl('', {
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
    this.org.tenant().subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.tenantName.setValue(tenant.name ?? '');
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
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

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }
}
