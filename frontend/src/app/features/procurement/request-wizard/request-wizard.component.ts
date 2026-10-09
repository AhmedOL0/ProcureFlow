import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';

import { RequestFormDialogComponent, RequestFormData, RequestFormResult } from '../dialogs/request-form.dialog';

/**
 * Thin creation entry: opens the draft dialog immediately and lands on the
 * new dossier, where lines are added. Kept as a route so deep links work.
 */
@Component({
  selector: 'app-request-wizard',
  template: '',
})
export class RequestWizardComponent {
  private readonly dialogs = inject(MatDialog);
  private readonly router = inject(Router);

  constructor() {
    const dialog = this.dialogs.open<RequestFormDialogComponent, RequestFormData, RequestFormResult>(
      RequestFormDialogComponent,
      { width: '480px', data: { mode: 'create' }, disableClose: true },
    );
    dialog.afterClosed().subscribe((result) => {
      if (result?.id) {
        void this.router.navigate(['/requests', result.id]);
      } else {
        void this.router.navigate(['/requests']);
      }
    });
  }
}
