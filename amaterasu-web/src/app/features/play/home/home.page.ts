import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { ago, initials, ordinal, points, signed } from '../../../shared/format';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { activityLine } from '../data/activity';
import { LiveNoteComponent } from '../labs/live-note.component';
import { TeamLabCardComponent } from '../labs/team-lab-card.component';
import { HomeStore } from './home.store';

/** The participant home: the team's environments with live state, the labs you joined and team activity. */
@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, IconComponent, EmptyStateComponent, SkeletonComponent, TeamLabCardComponent,
    LiveNoteComponent],
  providers: [HomeStore],
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
  host: { class: 'content' },
})
export class HomePage {
  protected readonly session = inject(SessionStore);
  protected readonly trackers = inject(LabTrackersStore);
  protected readonly rooms = inject(RoomsStore);
  protected readonly home = inject(HomeStore);

  protected readonly fmt = { ordinal, points, signed, ago, initials };

  protected readonly teamName = computed(() => this.session.team()?.name ?? '');
  protected readonly labCount = computed(() => this.trackers.teamLabs().length);

  protected readonly subtitle = computed(() => {
    const parts: string[] = [];
    const mates = this.home.teammates().length;
    if (mates) parts.push(`${mates} ${mates === 1 ? 'member' : 'members'} in this lab`);
    parts.push(`${this.labCount()} team ${this.labCount() === 1 ? 'environment' : 'environments'}`);
    const room = this.rooms.currentRoom();
    if (room) parts.push(`playing ${room.name}`);
    return parts.join(' · ');
  });

  protected readonly current = computed(() => this.home.current());
  protected readonly solvedPct = computed(() => {
    const current = this.current();
    const total = current?.challenges.length ?? 0;
    return total ? Math.round(((current?.me.solved ?? 0) / total) * 100) : 0;
  });

  protected readonly roomCards = computed(() => this.rooms.playedRooms().map(room => {
    const overview = this.home.overviews()[room.id!];
    const total = overview?.challenges.length ?? 0;
    const solved = overview?.me.solved ?? 0;
    return {
      id: room.id!,
      name: room.name,
      rank: overview?.me.rank ?? 0,
      points: overview?.me.points ?? 0,
      solved,
      total,
      pct: total ? Math.round((solved / total) * 100) : 0,
    };
  }));

  protected readonly activity = computed(() => this.home.activity().map(activityLine));
}
