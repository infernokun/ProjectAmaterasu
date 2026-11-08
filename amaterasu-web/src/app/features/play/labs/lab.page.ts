import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CrumbsService } from '../../../core/crumbs.service';
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
import { ScenarioCardComponent } from './scenario-card.component';

/** A lab: its scenario, its environment, its questions and the leaderboard. */
@Component({
  selector: 'app-lab-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TitleCasePipe, IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent,
    ChallengeDetailComponent, EnvironmentBarComponent, ScenarioCardComponent, LabLeaderboardComponent],
  providers: [RoomPlayStore],
  templateUrl: './lab.page.html',
  styleUrl: './lab.page.scss',
  host: { class: 'content' },
})
export class LabPage {
  protected readonly store = inject(RoomPlayStore);
  protected readonly session = inject(SessionStore);
  private readonly crumbs = inject(CrumbsService);
  private readonly router = inject(Router);
  private readonly api = inject(PlayApiService);

  readonly labId = input.required<string>();
  /** The selected question, kept in the address so it can be shared. */
  readonly challenge = input<string>();

  private readonly files = inject(LabFileDownload);
  protected readonly fmt = { ordinal, points };
  protected readonly size = fileSize;
  protected readonly statuses: { value: StatusFilter; label: string }[] = [
    { value: 'all', label: 'All' }, { value: 'open', label: 'Open' }, { value: 'solved', label: 'Solved' },
  ];

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

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => this.store.load(id));
    });
    effect(() => {
      const id = this.challenge();
      if (id) untracked(() => this.store.select(id));
    });
    effect(() => {
      const name = this.overview()?.room.name;
      if (name) untracked(() => this.crumbs.set([{ label: 'Labs', link: '/labs' }, { label: name }]));
    });
    // /labs/<id> used to open a team's lab copy; send those addresses to the copy's page
    effect(() => {
      if (this.store.error() && !this.overview()) untracked(() => this.tryOldAddress(this.labId()));
    });
  }

  private triedOldAddress = false;

  private async tryOldAddress(id: string): Promise<void> {
    if (this.triedOldAddress) return;
    this.triedOldAddress = true;
    try {
      await firstValueFrom(this.api.tracker(id));
      this.router.navigate(['/environments', id], { replaceUrl: true });
    } catch {
      // Not a copy either; the page shows the error
    }
  }

  protected download(fileId: string, name: string): void {
    this.files.save(this.labId(), fileId, name);
  }

  protected pick(id: string): void {
    this.store.select(id);
    this.router.navigate([], { queryParams: { challenge: id }, replaceUrl: true });
    // On narrow screens the detail sits under the list
    if (window.matchMedia?.('(max-width: 959.98px)').matches) {
      setTimeout(() => document.getElementById('ch-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    }
  }
}
