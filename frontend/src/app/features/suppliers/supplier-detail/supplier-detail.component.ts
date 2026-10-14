import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService, getDefaultLanguage } from '../../../core/i18n/language.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { Pencil, Plus, Star, Tags, Trash2, Truck } from '../../../shared/icons';
import { ApiFailure, fallbackMessage, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { formatDecimal } from '../../../shared/utils/format';
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
  protected readonly lang = inject(LanguageService);

  protected readonly icons = { pencil: Pencil, plus: Plus, star: Star, tags: Tags, trash: Trash2, truck: Truck };
  protected readonly canWrite = this.auth.hasAuthority('supplier:write');

  protected readonly loading = signal(true);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly supplier = signal<Supplier | null>(null);
  protected readonly contacts = signal<SupplierContact[]>([]);
  protected readonly categories = signal<SupplierCategory[]>([]);
  protected readonly performances = signal<SupplierPerformance[]>([]);

  /** Header facts from real scorecards: average on-time and coverage. */
  protected readonly scoreSummary = computed(() => {
    const rates = this.performances()
      .map((s) => s.onTimeRate)
      .filter((r): r is number => typeof r === 'number');
    if (rates.length === 0) {
      return null;
    }
    const avg = rates.reduce((a, b) => a + b, 0) / rates.length;
    return { avg: Math.round(avg * 10) / 10, periods: this.performances().length };
  });

  private supplierId(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  protected crumbsFor(name: string | null | undefined): { label: string; url?: string }[] {
    return [
      { label: this.lang.t('nav.suppliers'), url: '/suppliers' },
      { label: name ?? this.lang.t('req.fallbackCrumb') },
    ];
  }

  protected headerDesc(taxId: string | null | undefined): string {
    return taxId
      ? this.lang.tx('sup.taxLine', { tax: taxId })
      : this.lang.t('sup.noTaxFile');
  }

  protected scoreLabel(avg: number, periods: number): string {
    const rendered = formatDecimal(avg);
    return periods === 1
      ? this.lang.tx('sup.scoreOne', { avg: rendered })
      : this.lang.tx('sup.scoreMany', { avg: rendered, n: periods });
  }

  constructor() {
    this.reload();
  }

  reload(): void {
    const id = this.supplierId();
    if (!id) {
      this.failure.set({ status: 404, code: null, message: fallbackMessage(404, getDefaultLanguage()), details: {} });
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
          title: this.lang.tx('sup.deleteTitle', { name: current.name ?? this.lang.t('sup.fallbackTitle') }),
          message: this.lang.t('sup.deleteMsg'),
          confirmLabel: this.lang.t('sup.deleteConfirm'),
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
        title: this.lang.tx('sup.removeContactTitle', { name: contact.name ?? 'contact' }),
        message: this.lang.t('sup.removeContactMsg'),
        confirmLabel: this.lang.t('sup.removeContactConfirm'),
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
