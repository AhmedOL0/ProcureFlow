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
import { type I18nKey, LanguageService } from '../../core/i18n/language.service';
import { NotificationCenterService } from '../../core/notifications/notification-center.service';
import { ThemeService } from '../../core/theme/theme.service';
import {
  Bell,
  Building2,
  ChartColumn,
  ClipboardList,
  ChevronsUpDown,
  FileClock,
  Globe,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Menu,
  Moon,
  Network,
  Receipt,
  Settings,
  ShieldCheck,
  Sparkles,
  Stamp,
  Sun,
  Truck,
  User,
  Users,
  Wallet,
} from '../../shared/icons';
import { Breadcrumb } from '../../shared/components/page-header/page-header.component';
import { humanizeRole, initialsFor } from '../../shared/utils/names';

interface NavItem {
  labelKey: I18nKey;
  url: string;
  icon: typeof Truck;
  /** Any-of authorities required to see the entry; absent means visible. */
  authorities?: string[];
  /** Exact URL match; needed for section roots with children (/requests, /admin). */
  exact?: boolean;
}

interface NavSection {
  labelKey: I18nKey;
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
  protected readonly lang = inject(LanguageService);
  private readonly themes = inject(ThemeService);

  protected readonly icons = {
    bell: Bell,
    menu: Menu,
    globe: Globe,
    moon: Moon,
    sun: Sun,
    dashboard: LayoutDashboard,
    truck: Truck,
    user: User,
    chevrons: ChevronsUpDown,
    logout: LogOut,
    sparkles: Sparkles,
  };
  protected readonly canSeeCopilot = this.auth.hasAuthority('ai:use');
  protected readonly isDark = computed(() => this.themes.theme() === 'dark');
  protected readonly themeLabel = computed(() =>
    this.isDark() ? this.lang.t('nav.themeLight') : this.lang.t('nav.themeDark'),
  );

  protected toggleTheme(): void {
    this.themes.toggle();
  }

  protected useLanguage(lang: 'en' | 'fr'): void {
    this.lang.use(lang);
  }

  /** Display name: profile names when set, else the email handle. */
  protected readonly displayName = computed(() => {
    const user = this.auth.currentUser();
    const full = `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim();
    if (full) {
      return full;
    }
    return (user?.email ?? '?').split('@')[0] ?? '?';
  });

  protected readonly avatarInitials = computed(() => initialsFor(this.displayName()));

  protected readonly primaryRole = computed(() => {
    const user = this.auth.currentUser();
    return humanizeRole(user?.roles?.[0], this.lang.lang());
  });

  readonly nav: NavSection[] = [
    {
      labelKey: 'nav.sectionWorkspace',
      items: [
        { labelKey: 'nav.dashboard', url: '/dashboard', icon: LayoutDashboard },
        { labelKey: 'nav.workspace', url: '/workspace', icon: User },
      ],
    },
    {
      labelKey: 'nav.sectionProcurement',
      items: [
        { labelKey: 'nav.requests', url: '/requests/mine', icon: FileClock },
        { labelKey: 'nav.purchaseRequests', url: '/requests', icon: ClipboardList, exact: true },
        { labelKey: 'nav.approvals', url: '/approvals', icon: Stamp },
        { labelKey: 'nav.suppliers', url: '/suppliers', icon: Building2 },
        { labelKey: 'nav.orders', url: '/orders', icon: Truck },
        { labelKey: 'nav.invoices', url: '/invoices', icon: Receipt },
        { labelKey: 'nav.budgets', url: '/budgets', icon: Wallet, authorities: ['budget:read'] },
      ],
    },
    {
      labelKey: 'nav.sectionIntelligence',
      items: [
        {
          labelKey: 'nav.analytics',
          url: '/analytics',
          icon: ChartColumn,
          authorities: ['analytics:read'],
        },
        { labelKey: 'nav.copilotFull', url: '/copilot', icon: Sparkles, authorities: ['ai:use'] },
      ],
    },
    {
      labelKey: 'nav.sectionAdmin',
      items: [
        {
          labelKey: 'nav.adminWorkspace',
          url: '/admin',
          icon: Settings,
          exact: true,
          authorities: ['tenant:manage', 'department:manage', 'user:manage'],
        },
        { labelKey: 'nav.usersRoles', url: '/admin/users', icon: Users, authorities: ['user:manage'] },
        {
          labelKey: 'nav.departments',
          url: '/admin/departments',
          icon: Network,
          authorities: ['department:manage'],
        },
        {
          labelKey: 'nav.workflows',
          url: '/admin/workflows',
          icon: ListChecks,
          authorities: ['procurement:approve'],
        },
        {
          labelKey: 'nav.audit',
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
      const key = current.snapshot.data['breadcrumbKey'] as I18nKey | undefined;
      if (key) {
        trail.push({ label: this.lang.t(key) });
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
