import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs';

import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LanguageService } from '../i18n/language.service';

/** Screens with SavableDrafts opt into the unsaved-work prompt. */
export interface SavableDraft {
  hasUnsavedDraft(): boolean;
}

/**
 * Leaves a dirty creation form only on explicit confirmation. Read-only
 * screens and successful saves (which clear the dirty flag first) pass
 * straight through — the prompt appears solely on accidental loss.
 */
export const pendingChangesGuard: CanDeactivateFn<SavableDraft> = (component) => {
  if (!component.hasUnsavedDraft()) {
    return true;
  }
  const dialogs = inject(MatDialog);
  const lang = inject(LanguageService);
  const dialog = dialogs.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
    ConfirmDialogComponent,
    {
      width: '400px',
      data: {
        title: lang.t('guard.discardTitle'),
        message: lang.t('guard.discardMsg'),
        confirmLabel: lang.t('guard.discardConfirm'),
      },
    },
  );
  return dialog.afterClosed().pipe(map((confirmed) => confirmed === true));
};
