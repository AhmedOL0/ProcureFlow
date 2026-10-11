import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';

import { ChevronRight } from '../../../shared/icons';

export interface Breadcrumb {
  label: string;
  url?: string;
}

/**
 * Page head: breadcrumb trail, title, description and a projected action
 * row — the same rhythm on every feature screen.
 */
@Component({
  selector: 'pf-page-header',
  imports: [RouterLink, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.scss',
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly description = input<string>('');
  readonly breadcrumbs = input<Breadcrumb[]>([]);
  protected readonly chevron = ChevronRight;
}
