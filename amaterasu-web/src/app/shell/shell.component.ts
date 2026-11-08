import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { filter, map } from 'rxjs';
import { PageData } from '../core/page';
import { Crumb, CrumbsService } from '../core/crumbs.service';
import { LabTrackersStore } from '../core/stores/lab-trackers.store';
import { RoomsStore } from '../core/stores/rooms.store';
import { SessionStore } from '../core/stores/session.store';
import { ThemeStore } from '../core/theme/theme.store';
import { IconComponent } from '../shared/ui/icon.component';
import { GlobalSearchComponent } from './global-search.component';
import { NavCountKey, navFor } from './nav';
import { NotificationBellComponent } from './notification-bell.component';

const NARROW = '(max-width: 959.98px)';

/** Signed-in layout: role-filtered sidebar, top bar with breadcrumb and user, and the page. */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavContainer, MatSidenav, MatSidenavContent, IconComponent,
    GlobalSearchComponent, NotificationBellComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly crumbsService = inject(CrumbsService);
  protected readonly session = inject(SessionStore);
  protected readonly rooms = inject(RoomsStore);
  private readonly trackers = inject(LabTrackersStore);

  private readonly content = viewChild(MatSidenavContent);

  constructor() {
    // Applies the saved theme as soon as someone signs in
    inject(ThemeStore);
    effect(() => {
      if (!this.narrow()) {
        this.drawerOpen.set(false);
      }
    });
    // The page scrolls inside the sidenav content, not the window
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.content()?.scrollTo({ top: 0 }));
  }

  protected readonly narrow = toSignal(inject(BreakpointObserver).observe(NARROW).pipe(map(state => state.matches)), {
    initialValue: false,
  });
  protected readonly drawerOpen = signal(false);

  protected readonly groups = computed(() => navFor(this.session.roles()));

  // Labs the user joined
  private readonly counts = computed<Record<NavCountKey, number>>(() => ({
    labs: this.rooms.joinedRooms().length,
  }));

  protected countFor(key: NavCountKey | undefined): number | null {
    if (!key) {
      return null;
    }
    const value = this.counts()[key];
    return value > 0 ? value : null;
  }

  private readonly page = toSignal(
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      map(() => deepestData(this.router.routerState.snapshot.root)),
    ),
    { initialValue: deepestData(this.router.routerState.snapshot.root) },
  );

  protected readonly legacyPage = computed(() => !!this.page()?.legacy);

  protected readonly crumbs = computed<Crumb[]>(() => {
    const custom = this.crumbsService.custom();
    if (custom) {
      return custom;
    }
    const page = this.page();
    if (!page?.title) {
      return [];
    }
    return page.section ? [{ label: page.section }, { label: page.title }] : [{ label: page.title }];
  });

  protected readonly teamLabel = computed(() => this.session.team()?.name ?? 'No team');

  protected navigated(): void {
    if (this.narrow()) {
      this.drawerOpen.set(false);
    }
  }

  protected signOut(): void {
    this.session.signOut();
  }
}

function deepestData(root: ActivatedRouteSnapshot): PageData | undefined {
  let route: ActivatedRouteSnapshot | null = root;
  let data: PageData | undefined;
  while (route) {
    if (route.data && (route.data as PageData).title) {
      data = route.data as PageData;
    }
    route = route.firstChild;
  }
  return data;
}
