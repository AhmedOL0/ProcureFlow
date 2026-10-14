import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { Truck } from '../../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../../shared/utils/api-errors';
import { runDownload } from '../../../shared/utils/download';
import { formatMinor } from '../../../shared/utils/money';
import { SuppliersService } from '../../suppliers/suppliers.service';
import { OrderService, PurchaseOrder } from '../orders.service';
import { ReceiveDialogComponent, ReceiveDialogResult } from '../dialogs/receive.dialog';

/**
 * Order file: snapshot lines with receipt progress, the originating
 * request link, and exactly the transitions the backend permits from the
 * current status. Over-receipt and wrong-state transitions surface as
 * 409s with a message — never silent.
 */
@Component({
  selector: 'app-order-detail',
  imports: [
    RouterLink, MatButtonModule, MatProgressSpinnerModule, MatSnackBarModule, LucideAngularModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, ErrorStateComponent,
  ],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
})
export class OrderDetailComponent {
  private readonly orders = inject(OrderService);
  private readonly suppliers = inject(SuppliersService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(MatDialog);
  private readonly snacks = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = { truck: Truck };
  protected readonly formatMinor = formatMinor;
  protected readonly canWrite = this.auth.hasAuthority('order:write');

  protected readonly loading = signal(true);
  protected readonly acting = signal(false);
  protected readonly busyExport = signal(false);
  protected readonly exportError = signal<string | null>(null);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly order = signal<PurchaseOrder | null>(null);
  protected readonly supplierName = signal<string>('...');

  protected readonly status = computed(() => this.order()?.status ?? null);
  protected readonly canSend = computed(() => this.status() === 'DRAFT');
  protected readonly canReceive = computed(
    () => this.status() === 'SENT' || this.status() === 'PARTIALLY_RECEIVED',
  );
  protected readonly canClose = computed(() => this.status() === 'RECEIVED');
  protected readonly canCancel = computed(
    () => this.status() === 'DRAFT' || this.status() === 'SENT',
  );
  protected readonly receivedTotal = computed(
    () => (this.order()?.lines ?? []).reduce((sum, line) => sum + (line.receivedQty ?? 0), 0),
  );
  protected readonly orderedTotal = computed(
    () => (this.order()?.lines ?? []).reduce((sum, line) => sum + (line.quantity ?? 0), 0),
  );

  protected readonly receiptPct = computed(() => {
    const ordered = this.orderedTotal();
    if (ordered <= 0) {
      return 0;
    }
    return Math.min(100, (this.receivedTotal() / ordered) * 100);
  });

  /** Fulfillment pipeline position; cancelled ends the line early. */
  protected readonly steps = ['DRAFT', 'SENT', 'RECEIVED', 'CLOSED'];
  protected readonly cancelled = computed(() => this.status() === 'CANCELLED');

  protected stepState(step: string): 'done' | 'current' | 'todo' {
    const order = ['DRAFT', 'SENT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED'];
    const at = order.indexOf(this.status() ?? '');
    const position = order.indexOf(step);
    if (at < 0 || position < 0 || this.status() === 'CANCELLED') {
      return 'todo';
    }
    if (position < at) {
      return 'done';
    }
    return position === at ? 'current' : 'todo';
  }

  private orderId(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  constructor() {
    this.reload();
  }

  reload(): void {
    const id = this.orderId();
    if (!id) {
      this.failure.set({ status: 404, code: null, message: 'This record does not exist in your workspace.', details: {} });
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.failure.set(null);
    this.orders.get(id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
        this.resolveSupplier(order.supplierId);
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

  private resolveSupplier(supplierId: string | null | undefined): void {
    if (!supplierId) {
      this.supplierName.set('N/A');
      return;
    }
    this.suppliers.get(supplierId).subscribe({
      next: (supplier) => this.supplierName.set(supplier.name ?? '?'),
      error: () => this.supplierName.set('?'),
    });
  }

  send(): void {
    this.transition('sent', (id) => this.orders.send(id));
  }

  /** Printable order as PDF; failures show inline below the header. */
  downloadPdf(): void {
    const current = this.order();
    if (!current?.id) {
      return;
    }
    runDownload(
      this.orders.document(current.id),
      this.busyExport,
      this.exportError,
      (error: unknown) => userMessageFor(parseApiFailure(error)),
    );
  }

  close(): void {
    this.transition('closed', (id) => this.orders.close(id));
  }

  cancel(): void {
    this.transition('cancelled', (id) => this.orders.cancel(id));
  }

  private transition(verb: string, call: (id: string) => ReturnType<OrderService['send']>): void {
    const current = this.order();
    if (!current?.id || this.acting()) {
      return;
    }
    this.acting.set(true);
    call(current.id).subscribe({
      next: () => {
        this.acting.set(false);
        this.snacks.open(`Order ${verb}.`, 'Dismiss', { duration: 4000 });
        this.reload();
      },
      error: (error: unknown) => {
        this.acting.set(false);
        this.failure.set(parseApiFailure(error));
      },
    });
  }

  openReceive(): void {
    const current = this.order();
    if (!current?.id) {
      return;
    }
    const dialog = this.dialogs.open<ReceiveDialogComponent, { lines: typeof current.lines }, ReceiveDialogResult>(
      ReceiveDialogComponent,
      { width: '520px', data: { lines: current.lines ?? [] } },
    );
    dialog.afterClosed().subscribe((result) => {
      if (!result || !current.id) {
        return;
      }
      this.acting.set(true);
      this.orders.receive(current.id, result.lines).subscribe({
        next: () => {
          this.acting.set(false);
          this.snacks.open('Receipt recorded.', 'Dismiss', { duration: 4000 });
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
