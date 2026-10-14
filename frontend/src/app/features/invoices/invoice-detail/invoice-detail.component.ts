import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService, getDefaultLanguage } from '../../../core/i18n/language.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { FileText } from '../../../shared/icons';
import { ApiFailure, fallbackMessage, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { runDownload } from '../../../shared/utils/download';
import { formatMinor } from '../../../shared/utils/money';
import { Invoice, InvoiceService } from '../invoices.service';
import { PayDialogComponent, PayDialogResult } from '../dialogs/pay.dialog';

/**
 * Invoice file: 3-way match per order line (ordered vs received vs
 * invoiced), payment progress and recording. Overpaying is rejected with
 * 409. Recording a payment tracks status — it never transfers money.
 */
@Component({
  selector: 'app-invoice-detail',
  imports: [
    RouterLink, MatButtonModule, MatProgressSpinnerModule, MatSnackBarModule, LucideAngularModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './invoice-detail.component.html',
  styleUrl: './invoice-detail.component.scss',
})
export class InvoiceDetailComponent {
  private readonly invoices = inject(InvoiceService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);
  private readonly snacks = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  protected readonly lang = inject(LanguageService);

  protected readonly crumbs = computed(() => [
    { label: this.lang.t('nav.invoices'), url: '/invoices' },
    { label: this.invoice()?.number ?? this.lang.t('req.fallbackCrumb') },
  ]);

  protected titleFor(number: string | null | undefined): string {
    return this.lang.tx('inv.title', { n: number ?? '' });
  }

  protected paidLine(total: string): string {
    return this.lang.tx('inv.paidLine', { x: total });
  }

  protected readonly icons = { file: FileText };
  protected readonly formatMinor = formatMinor;
  protected readonly canWrite = this.auth.hasAuthority('invoice:write');

  protected readonly loading = signal(true);
  protected readonly acting = signal(false);
  protected readonly busyExport = signal(false);
  protected readonly exportError = signal<string | null>(null);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly invoice = signal<Invoice | null>(null);

  private invoiceId(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  constructor() {
    this.reload();
  }

  reload(): void {
    const id = this.invoiceId();
    if (!id) {
      this.failure.set({ status: 404, code: null, message: fallbackMessage(404, getDefaultLanguage()), details: {} });
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.failure.set(null);
    this.invoices.get(id).subscribe({
      next: (invoice) => {
        this.invoice.set(invoice);
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

  protected remaining(): number {
    const invoice = this.invoice();
    return Math.max((invoice?.totalMinor ?? 0) - (invoice?.paidMinor ?? 0), 0);
  }

  /** Printable invoice as PDF; failures show inline below the header. */
  downloadPdf(): void {
    const current = this.invoice();
    if (!current?.id) {
      return;
    }
    runDownload(
      this.invoices.document(current.id),
      this.busyExport,
      this.exportError,
      (error: unknown) => userMessageFor(parseApiFailure(error)),
    );
  }

  openPay(): void {    const current = this.invoice();
    if (!current?.id || this.acting()) {
      return;
    }
    const dialog = this.dialogs.open<PayDialogComponent, { remaining: number; currency: string }, PayDialogResult>(
      PayDialogComponent,
      {
        width: '440px',
        data: { remaining: this.remaining(), currency: current.currency ?? 'MAD' },
      },
    );
    dialog.afterClosed().subscribe((result) => {
      if (!result || !current.id) {
        return;
      }
      this.acting.set(true);
      this.invoices.pay(current.id, result.amountMinor).subscribe({
        next: () => {
          this.acting.set(false);
          this.snacks.open(this.lang.t('inv.paidSnack'), this.lang.t('common.dismiss'), { duration: 4000 });
          this.reload();
        },
        error: (error: unknown) => {
          this.acting.set(false);
          this.failure.set(parseApiFailure(error));
        },
      });
    });
  }
}
