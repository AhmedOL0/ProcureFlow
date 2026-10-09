import { Component, input } from '@angular/core';

import { badgeLabelFor, badgeToneFor } from './status-badge';

/** Design-system status pill: tinted per state, uppercase, tracked. */
@Component({
  selector: 'pf-status-badge',
  template: `<span class="pf-badge pf-badge--{{ tone() }}">{{ label() }}</span>`,
})
export class StatusBadgeComponent {
  readonly status = input<string | null | undefined>(null);

  protected tone(): string {
    return badgeToneFor(this.status());
  }

  protected label(): string {
    return badgeLabelFor(this.status());
  }
}
