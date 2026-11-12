import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, NavigationStart, Router, RouterLink, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CrumbsService } from '../../../core/crumbs.service';
import { KEEP_SCROLL, PageData } from '../../../core/page';
import { SessionStore } from '../../../core/stores/session.store';
import { ordinal, points } from '../../../shared/format';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { PlayApiService } from '../data/play-api.service';
import { ChallengeDetailComponent } from '../rooms/challenge-detail.component';
import { RoomPlayStore, StatusFilter } from '../rooms/room-play.store';
import { EnvironmentBarComponent } from './environment-bar.component';
import { ENVIRONMENT_STATUS_META, environmentStatus, trackerOf } from './environment-status';
import { LabFileDownload, fileSize } from './lab-file-download';
import { LabLeaderboardComponent } from './lab-leaderboard.component';
import { LAB_SPLIT, SubPage, labCrumbs, labViews } from './lab-workspace';
import { ScenarioCardComponent } from './scenario-card.component';

/**
 * A lab: its scenario, its environment, its questions and the leaderboard. Its sub-pages (Details,
 * Console, Edit, Teams, Manage) open beside the questions on wide screens, and as a page of their
 * own with a way back on narrow ones.
 */
@Component({
  selector: 'app-lab-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterOutlet, TitleCasePipe, IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent,
    ChallengeDetailComponent, EnvironmentBarComponent, ScenarioCardComponent, LabLeaderboardComponent],
  providers: [RoomPlayStore],
  templateUrl: './lab.page.html',
  styleUrl: './lab.page.scss',
  host: { class: 'content lab-workspace', '[class.split]': 'split()' },
})
export class LabPage {
  protected readonly store = inject(RoomPlayStore);
  protected readonly session = inject(SessionStore);
  private readonly crumbs = inject(CrumbsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PlayApiService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly labId = input.required<string>();
  /** The selected question, kept in the address so it can be shared. */
  readonly challenge = input<string>();
  /** The copy a sub-page shows, when not the viewer's own. */
  readonly copy = input<string>();
  /** A sub-page popped out to the whole page on a wide screen. */
  readonly full = input<string>();

  private readonly files = inject(LabFileDownload);
  protected readonly fmt = { ordinal, points };
  protected readonly size = fileSize;
  protected readonly keepScroll = KEEP_SCROLL;
  protected readonly statuses: { value: StatusFilter; label: string }[] = [
    { value: 'all', label: 'All' }, { value: 'open', label: 'Open' }, { value: 'solved', label: 'Solved' },
  ];

  /** Two panes fit. */
  protected readonly split = inject(LAB_SPLIT);
  protected readonly sub = signal<SubPage | null>(null);
  /** The sub-page takes the whole page: always on narrow screens, and when popped out. */
  protected readonly whole = computed(() => !!this.sub() && (!this.split() || !!this.full()));
  private readonly navigations = signal(0);

  protected readonly overview = computed(() => this.store.overview());
  protected readonly environment = computed(() => this.overview()?.environment ?? null);
  protected readonly envStatus = computed(() => environmentStatus(trackerOf(this.environment())));
  protected readonly envMeta = computed(() => ENVIRONMENT_STATUS_META[this.envStatus()]);
  protected readonly byline = computed(() => {
    const room = this.overview()?.room;
    if (!room) return '';
    const parts: string[] = [];
    if (room.creator) parts.push(`Created by ${room.creator}`);
    if (room.facilitators.length) parts.push(`facilitated by ${room.facilitators.join(' and ')}`);
    return parts.join(' · ');
  });
  protected readonly views = computed(() => {
    const overview = this.overview();
    if (!overview) return [];
    return labViews(overview.room.id, overview.environment, { build: this.session.canBuild(), run: overview.room.canRun },
      this.store.selected()?.id);
  });
  /** Back to the questions, with the one that was open. */
  protected readonly back = computed(() => this.views()[0] ?? null);

  constructor() {
    this.sub.set(this.readSub());
    // Focus moves only for the viewer's own navigation, not on arrival
    let moved = false;
    this.router.events.pipe(takeUntilDestroyed()).subscribe(event => {
      if (event instanceof NavigationStart) moved = true;
      if (!(event instanceof NavigationEnd)) return;
      const before = this.sub();
      const now = this.readSub();
      this.sub.set(now);
      this.navigations.update(count => count + 1);
      if (moved && before?.key !== now?.key) this.focusAfterChange(!!now);
    });

    effect(() => {
      const id = this.labId();
      untracked(() => this.store.load(id));
    });
    effect(() => {
      const id = this.challenge();
      if (id) untracked(() => this.store.select(id));
    });
    // Navigation clears the breadcrumb, so it is set again after each one
    effect(() => {
      this.navigations();
      const room = this.overview()?.room;
      const sub = this.sub();
      const copy = this.copy();
      if (room) untracked(() => this.crumbs.set(labCrumbs(room.id, room.name, sub, copy)));
    });
    // /labs/<id> used to open a team's lab copy; send those addresses to the copy's page
    effect(() => {
      if (this.store.error() && !this.overview()) untracked(() => this.tryOldAddress(this.labId()));
    });
  }

  private readSub(): SubPage | null {
    const child = this.route.snapshot.firstChild;
    if (!child?.routeConfig?.path) return null;
    return { key: child.routeConfig.path.split('/')[0], title: (child.data as PageData).title };
  }

  private triedOldAddress = false;

  private async tryOldAddress(id: string): Promise<void> {
    if (this.triedOldAddress) return;
    this.triedOldAddress = true;
    const vmid = this.route.snapshot.firstChild?.paramMap.get('vmid');
    try {
      await firstValueFrom(this.api.tracker(id));
      this.router.navigate(vmid ? ['/environments', id, 'console', vmid] : ['/environments', id], { replaceUrl: true });
    } catch {
      // Not a copy either; the page shows the error
    }
  }

  protected download(fileId: string, name: string): void {
    this.files.save(this.labId(), fileId, name);
  }

  protected pick(id: string): void {
    this.store.select(id);
    if (this.sub()) {
      // Leaves the sub-page for the question, in the same place on the page
      this.router.navigate(['/labs', this.labId()], { queryParams: { challenge: id }, info: KEEP_SCROLL });
    } else {
      this.router.navigate([], { queryParams: { challenge: id }, replaceUrl: true, info: KEEP_SCROLL });
    }
    afterNextRender(() => this.revealDetail(), { injector: this.injector });
  }

  /** Pops a sub-page out to the whole page, or back beside the questions. */
  protected popOut(on: boolean): void {
    this.router.navigate([], { queryParams: { full: on ? 1 : null }, queryParamsHandling: 'merge', replaceUrl: true, info: KEEP_SCROLL });
  }

  /** Brings the detail into view only when it sits under the list, and only as far as needed. */
  private revealDetail(): void {
    const list = this.host.nativeElement.querySelector('.challenge-list');
    const detail = this.host.nativeElement.querySelector('app-challenge-detail');
    if (!list || !detail) return;
    if (detail.getBoundingClientRect().top < list.getBoundingClientRect().bottom) return;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    detail.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'nearest' });
  }

  /** Focus follows the pane that changed: the opened sub-page's heading, or back to the lab. */
  private focusAfterChange(opened: boolean): void {
    afterNextRender(() => {
      const root = this.host.nativeElement;
      const target = opened ? root.querySelector<HTMLElement>('#lab-pane-h')
        : this.split() ? root.querySelector<HTMLElement>('.side-pane') : root.querySelector<HTMLElement>('#lab-h');
      target?.focus({ preventScroll: true });
    }, { injector: this.injector });
  }
}
