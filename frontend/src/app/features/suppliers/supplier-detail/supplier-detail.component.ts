import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { Pencil, Plus, Star, Tags, Trash2, Truck } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { Supplier, SupplierCategory, SupplierContact, SupplierPerformance, SuppliersService } from '../suppliers.service';
import { SupplierFormDialogComponent, SupplierFormData, SupplierFormResult } from '../dialogs/supplier-form.dialog';
import { ContactDialogComponent, ContactDialogData, ContactDialogResult } from '../dialogs/contact.dialog';
import { CategoriesDialogComponent, CategoriesDialogData } from '../dialogs/categories.dialog';
import { PerformanceDialogComponent, PerformanceDialogData } from '../dialogs/performance.dialog';

/**
 * Supplier file: identity, contacts (single primary enforced server-side),
 * categories and scorecards. Every section owns its loading/error/empty
 * states; write actions hide without supplier:write.
 */
@Component({
  selector: 'app-supplier-detail',
  imports: [
    MatButtonModule, MatChipsModule, MatDialogModule, MatDividerModule,
    MatProgressSpinnerModule, LucideAngularModule, PageHeaderComponent, StatusBadgeComponent,
    EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './supplier-detail.component.html',
  styleUrl: './supplier-detail.component.scss',
})
export class SupplierDetailComponent {
  private readonly suppliers = inject(SuppliersService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly icons = { pencil: Pencil, plus: Plus, star: Star, tags: Tags, trash: Trash2, truck: Truck };
  protected readonly canWrite = this.auth.hasAuthority('supplier:write');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly supplier = signal<Supplier | null>(null);
  protected readonly contacts = signal<SupplierContact[]>([]);
  protected readonly categories = signal<SupplierCategory[]>([]);
  protected readonly performances = signal<SupplierPerformance[]>([]);

  private supplierId(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  constructor() {
    this.reload();
  }

  reload(): void {
    const id = this.supplierId();
    if (!id) {
      this.failure.set({ status: 404, code: null, message: 'This record does not exist in your workspace.' });
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.failure.set(null);
    forkJoin({
      supplier: this.suppliers.get(id),
      contacts: this.suppliers.contacts(id),
      categories: this.suppliers.categoriesOf(id),
      performances: this.suppliers.performances(id),
    }).subscribe({
      next: ({ supplier, contacts, categories, performances }) => {
        this.supplier.set(supplier);
        this.contacts.set(contacts ?? []);
        this.categories.set(categories ?? []);
        this.performances.set(performances ?? []);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(parseApiFailure(error));
        this.loading.set(false);
      },
    });
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }

  openEdit(): void {
    const current = this.supplier();
    if (!current) {
      return;
    }
    const dialog = this.dialogs.open<SupplierFormDialogComponent, SupplierFormData, SupplierFormResult>(
      SupplierFormDialogComponent,
      { width: '480px', data: { mode: 'edit', supplier: current } },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  confirmDelete(): void {
    const current = this.supplier();
    if (!current?.id) {
      return;
    }
    const dialog = this.dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
      ConfirmDialogComponent,
      {
        width: '400px',
        data: {
          title: `Delete ${current.name ?? 'supplier'}?`,
          message: 'Contacts and scorecards go with it. Orders pointing at this supplier block the delete.',
          confirmLabel: 'Delete supplier',
        },
      },
    );
    dialog.afterClosed().subscribe((confirmed) => {
      if (confirmed === true && current.id) {
        this.suppliers.remove(current.id).subscribe({
          next: () => void this.router.navigate(['/suppliers']),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }

  openContact(contact?: SupplierContact): void {
    const dialog = this.dialogs.open<ContactDialogComponent, ContactDialogData, ContactDialogResult>(
      ContactDialogComponent,
      {
        width: '480px',
        data: { supplierId: this.supplierId(), contact },
      },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  makePrimary(contact: SupplierContact): void {
    if (!contact.id || contact.primary) {
      return;
    }
    this.suppliers.updateContact(contact.id, { primary: true }).subscribe({
      next: () => this.reload(),
      error: (error: unknown) => this.failure.set(parseApiFailure(error)),
    });
  }

  removeContact(contact: SupplierContact): void {
    if (!contact.id) {
      return;
    }
    const dialog = this.dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: `Remove ${contact.name ?? 'contact'}?`,
        message: 'They will no longer be reachable from this supplier file.',
        confirmLabel: 'Remove contact',
      },
    });
    dialog.afterClosed().subscribe((confirmed) => {
      if (confirmed === true && contact.id) {
        this.suppliers.removeContact(contact.id).subscribe({
          next: () => this.reload(),
          error: (error: unknown) => this.failure.set(parseApiFailure(error)),
        });
      }
    });
  }

  manageCategories(): void {
    const dialog = this.dialogs.open<CategoriesDialogComponent, CategoriesDialogData, boolean>(
      CategoriesDialogComponent,
      {
        width: '480px',
        data: { supplierId: this.supplierId(), assigned: this.categories() },
      },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }

  recordPerformance(): void {
    const dialog = this.dialogs.open<PerformanceDialogComponent, PerformanceDialogData, boolean>(
      PerformanceDialogComponent,
      {
        width: '480px',
        data: { supplierId: this.supplierId() },
      },
    );
    dialog.afterClosed().subscribe((saved) => {
      if (saved) {
        this.reload();
      }
    });
  }
}
