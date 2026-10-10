import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatBadgeModule } from '@angular/material/badge';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationCenterService } from '../../core/notifications/notification-center.service';
import {
  Bell,
  Building2,
  ChartColumn,
  ClipboardList,
  FileText,
  Inbox,
  LayoutDashboard,
  Menu,
  Receipt,
  ScrollText,
  Settings,
  ShieldCheck,
  Sparkles,
  Truck,
  User,
  Users,
  Wallet,
} from '../../shared/icons';
import { Breadcrumb } from '../../shared/components/page-header/page-header.component';

interface NavItem {
  label: string;
  url: string;
  icon: typeof Truck;
  /** Any-of authorities required to see the entry; absent means visible. */
  authorities?: string[];
  /** Exact URL match; needed for section roots with children (/requests, /admin). */
  exact?: boolean;
}

interface NavSection {
  label: string;
  items: NavItem[];
}

/**
 * Authenticated shell: spruce rail, header with breadcrumbs, notification
 * inbox entry and user menu. Sections follow the page map (Workspace /
 * Procurement / Administration) but list only shipped modules — never
 * stubs for screens with no backend contract behind them. Entries the
 * current workspace role cannot use stay hidden (the backend adjudicates
 * regardless; this is navigation hygiene, not authorization).
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatBadgeModule,
    LucideAngularModule,
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

  protected readonly icons = {
    bell: Bell,
    menu: Menu,
    dashboard: LayoutDashboard,
    truck: Truck,
    user: User,
  };

  readonly nav: NavSection[] = [
    {
      label: 'Workspace',
      items: [
        { label: 'Dashboard', url: '/dashboard', icon: LayoutDashboard },
        { label: 'My Workspace', url: '/workspace', icon: User },
      ],
    },
    {
      label: 'Procurement',
      items: [
        { label: 'My Requests', url: '/requests/mine', icon: FileText },
        { label: 'Purchase Requests', url: '/requests', icon: ClipboardList, exact: true },
        { label: 'Approvals', url: '/approvals', icon: Inbox },
        { label: 'Suppliers', url: '/suppliers', icon: Building2 },
        { label: 'Orders', url: '/orders', icon: Truck },
        { label: 'Invoices', url: '/invoices', icon: Receipt },
        { label: 'Budgets & Spend', url: '/budgets', icon: Wallet, authorities: ['budget:read'] },
      ],
    },
    {
      label: 'Intelligence',
      items: [
        {
          label: 'Analytics',
          url: '/analytics',
          icon: ChartColumn,
          authorities: ['analytics:read'],
        },
        { label: 'ProcureAI Copilot', url: '/copilot', icon: Sparkles, authorities: ['ai:use'] },
      ],
    },
    {
      label: 'Administration',
      items: [
        {
          label: 'Workspace',
          url: '/admin',
          icon: Settings,
          exact: true,
          authorities: ['tenant:manage', 'department:manage', 'user:manage'],
        },
        { label: 'Users & Roles', url: '/admin/users', icon: Users, authorities: ['user:manage'] },
        {
          label: 'Departments',
          url: '/admin/departments',
          icon: Building2,
          authorities: ['department:manage'],
        },
        {
          label: 'Approval Policies',
          url: '/admin/workflows',
          icon: ScrollText,
          authorities: ['procurement:approve'],
        },
        {
          label: 'Audit Logs',
          url: '/admin/audit',
          icon: ShieldCheck,
          authorities: ['audit:read'],
        },
      ],
    },
  ];

  /** Sections with at least one entry the current role may see. */
  protected readonly visibleNav = computed<NavSection[]>(() =>
    this.nav
      .map((section) => ({
        ...section,
        items: section.items.filter(
          (item) =>
            !item.authorities || item.authorities.some((code) => this.auth.hasAuthority(code)),
        ),
      }))
      .filter((section) => section.items.length > 0),
  );

  /**
   * Compact viewports (tablet portrait and below) get the overlay drawer;
   * desktop keeps the open rail. Matches the 1024px layout breakpoint.
   */
  private readonly compact = toSignal(
    this.breakpoints.observe(['(max-width: 1023px)']).pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly drawerMode = computed(() => (this.compact() ? 'over' : 'side'));
  protected readonly drawerOpened = signal(true);

  private readonly navigation = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)),
    {
      initialValue: null,
    },
  );

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
    // Compact viewports land on a closed overlay drawer; desktop keeps the open rail.
    effect(() => {
      if (this.compact()) {
        this.drawerOpened.set(false);
      }
    });
    this.inbox.refresh();
  }

  toggleDrawer(): void {
    this.drawerOpened.update((open) => !open);
  }

  closeOnNavigate(): void {
    if (this.compact()) {
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
