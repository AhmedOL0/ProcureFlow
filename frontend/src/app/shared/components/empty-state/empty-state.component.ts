import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';

/** Empty collection state: what is empty, what to do next, one action. */
@Component({
  selector: 'pf-empty-state',
  imports: [MatButtonModule, LucideAngularModule],
  template: `
    <div class="pf-state">
      @if (icon()) {
        <lucide-angular [img]="icon()" [size]="28" class="pf-state__icon" />
      }
      <p class="pf-state__title">{{ title() }}</p>
      <p class="pf-state__message">{{ message() }}</p>
      @if (actionLabel()) {
        <button mat-flat-button color="primary" (click)="action.emit()">{{ actionLabel() }}</button>
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
export class EmptyStateComponent {
  readonly icon = input<LucideIconData | undefined>(undefined);
  readonly title = input.required<string>();
  readonly message = input<string>('');
  readonly actionLabel = input<string>('');
  readonly action = output<void>();
}
