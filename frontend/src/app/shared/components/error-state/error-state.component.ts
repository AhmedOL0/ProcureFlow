import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { LucideAngularModule } from 'lucide-angular';

import { CircleAlert } from '../../../shared/icons';

/**
 * Failure states with the right words: forbidden (no permission), missing
 * (not in this workspace) and generic (retryable). Never a blank screen.
 */
@Component({
  selector: 'pf-error-state',
  imports: [MatButtonModule, LucideAngularModule],
  template: `
    <div class="pf-state" role="alert">
      <lucide-angular [img]="icon" [size]="28" class="pf-state__icon pf-state__icon--danger" />
      <p class="pf-state__title">{{ title() }}</p>
      <p class="pf-state__message">{{ message() }}</p>
      @if (retryLabel()) {
        <button mat-stroked-button (click)="retry.emit()">{{ retryLabel() }}</button>
      }
    </div>
  `,
  styles: [
    `
      .pf-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--pf-space-sm);
        padding: var(--pf-space-2xl);
        text-align: center;
      }
      .pf-state__icon {
        color: var(--pf-slate);
      }
      .pf-state__icon--danger {
        color: var(--pf-danger-text);
      }
      .pf-state__title {
        font: var(--pf-headline-sm);
        color: var(--pf-navy);
        margin: 0;
      }
      .pf-state__message {
        font: var(--pf-body-md);
        color: var(--pf-slate);
        margin: 0 0 var(--pf-space-sm);
        max-width: 28rem;
      }
    `,
  ],
})
export class ErrorStateComponent {
  readonly title = input<string>('Something went wrong');
  readonly message = input<string>('Try again.');
  readonly retryLabel = input<string>('');
  readonly retry = output<void>();
  protected readonly icon = CircleAlert;
}
