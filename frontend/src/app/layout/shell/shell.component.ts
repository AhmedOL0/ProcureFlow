import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatBadgeModule } from '@angular/material/badge';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationCenterService } from '../../core/notifications/notification-center.service';
import { Bell, LayoutDashboard, Menu, Truck, User } from '../../shared/icons';
import { Breadcrumb } from '../../shared/components/page-header/page-header.component';

interface NavItem {
  label: string;
  url: string;
  icon: typeof Truck;
}

/**
 * Authenticated shell: navy rail, header with breadcrumbs, notification
 * inbox entry and user menu. The rail lists only shipped modules —
 * dashboard and suppliers — never stubs.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatButtonModule,
    MatIconModule, MatMenuModule, MatBadgeModule, LucideAngularModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly breakpoints = inject(BreakpointObserver);
  protected readonly auth = inject(AuthService);
  protected readonly inbox = inject(NotificationCenterService);

  protected readonly icons = { bell: Bell, menu: Menu, dashboard: LayoutDashboard, truck: Truck, user: User };

  readonly nav: NavItem[] = [
    { label: 'Dashboard', url: '/dashboard', icon: LayoutDashboard },
    { label: 'Suppliers', url: '/suppliers', icon: Truck },
  ];

  private readonly handset = toSignal(
    this.breakpoints.observe([Breakpoints.Handset]).pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly drawerMode = computed(() => (this.handset() ? 'over' : 'side'));
  protected readonly drawerOpened = signal(true);

  private readonly navigation = toSignal(this.router.events.pipe(filter((e) => e instanceof NavigationEnd)), {
    initialValue: null,
  });

  protected readonly breadcrumbs = computed<Breadcrumb[]>(() => {
    void this.navigation();
    const trail: Breadcrumb[] = [];
    let current: ActivatedRoute | null = this.route.firstChild;
    while (current) {
      const label = current.snapshot.data['breadcrumb'] as string | undefined;
      if (label) {
        trail.push({ label });
      }
      current = current.firstChild;
    }
    return trail;
  });

  constructor() {
    // Handsets land on a closed overlay drawer; desktop keeps the open rail.
    effect(() => {
      if (this.handset()) {
        this.drawerOpened.set(false);
      }
    });
    this.inbox.refresh();
  }

  toggleDrawer(): void {
    this.drawerOpened.update((open) => !open);
  }

  closeOnNavigate(): void {
    if (this.handset()) {
      this.drawerOpened.set(false);
    }
  }

  openInbox(): void {
    this.inbox.refresh();
  }

  read(item: { id?: string }): void {
    if (item.id) {
      this.inbox.markRead(item.id);
    }
  }

  signOut(): void {
    this.auth.logout();
  }
}
