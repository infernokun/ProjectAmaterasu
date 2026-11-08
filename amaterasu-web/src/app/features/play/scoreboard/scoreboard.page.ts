import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CrumbsService } from '../../../core/crumbs.service';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { clock, dateTime, initials, ordinal, points, toDate } from '../../../shared/format';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { PointsChartComponent } from './points-chart.component';
import { ScoreView, ScoreboardStore } from './scoreboard.store';
import { topSeries, trend } from './scoreboard-view';

interface Row {
  id: string;
  rank: number;
  name: string;
  sub: string;
  points: number;
  solves: number;
  hints: number;
  last: string;
  trend: { label: string; tone: string };
  you: boolean;
  link: string[] | null;
}

/** Players and teams in a room, with points over time. */
@Component({
  selector: 'app-scoreboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent, PointsChartComponent],
  providers: [ScoreboardStore],
  templateUrl: './scoreboard.page.html',
  styleUrl: './scoreboard.page.scss',
  host: { class: 'content' },
})
export class ScoreboardPage {
  protected readonly store = inject(ScoreboardStore);
  private readonly rooms = inject(RoomsStore);
  private readonly session = inject(SessionStore);
  private readonly crumbs = inject(CrumbsService);

  /** From /labs/:roomId/scoreboard; /scoreboard uses the current lab. */
  readonly roomId = input<string>();

  protected readonly room = computed(() => this.roomId() ?? this.rooms.currentRoom()?.id ?? null);
  protected readonly teams = computed(() => this.store.view() === 'teams');
  protected readonly fmt = { ordinal, points, initials };

  constructor() {
    this.rooms.load();
    effect(() => {
      const id = this.room();
      if (id) untracked(() => this.store.load(id));
    });
    effect(() => {
      const name = this.store.standings()?.roomName;
      const id = this.room();
      if (name && id) untracked(() => this.crumbs.set([{ label: 'Labs', link: '/labs' }, { label: name, link: ['/labs', id] }, { label: 'Scoreboard' }]));
    });
  }

  private readonly allRows = computed<Row[]>(() => {
    const roomId = this.room();
    if (this.teams()) {
      return this.store.teams().map(team => ({
        id: team.teamId, rank: team.rank, name: team.name, sub: memberCount(team.teamSize ?? team.members),
        points: team.points, solves: team.solves, hints: team.hintsUsed, last: clock(team.lastSolveAt) || '-',
        trend: trend(team.lastHourChange), you: team.teamId === this.store.myTeam()?.teamId, link: null,
      }));
    }
    return this.store.players().map(player => ({
      id: player.userId, rank: player.rank, name: player.username, sub: player.teamName ?? 'No team',
      points: player.points, solves: player.solves, hints: player.hintsUsed, last: clock(player.lastSolveAt) || '-',
      trend: trend(player.lastHourChange), you: player.userId === this.session.userId(),
      link: roomId ? ['/labs', roomId, 'scoreboard', player.username] : null,
    }));
  });

  protected readonly rows = computed(() => {
    const query = this.store.query().trim().toLowerCase();
    return query ? this.allRows().filter(row => `${row.name} ${row.sub}`.toLowerCase().includes(query)) : this.allRows();
  });

  protected readonly podium = computed(() => this.allRows().slice(0, 3).map((row, i) => ({
    ...row,
    place: ['1st place', '2nd place', '3rd place'][i],
    note: `${row.solves} ${row.solves === 1 ? 'solve' : 'solves'}${row.last !== '-' ? ' · last ' + row.last : ''}`,
  })));

  protected readonly you = computed(() => {
    const rows = this.allRows();
    const index = rows.findIndex(row => row.you);
    if (index < 0) {
      return { label: this.teams() ? 'Your team' : 'Your rank', value: '-', note: this.teams() ? 'Your team has no players here yet' : 'Join the lab to get a rank' };
    }
    const row = rows[index];
    const label = this.teams() ? row.name : 'Your rank';
    if (index === 0) {
      const gap = rows[1] ? row.points - rows[1].points : 0;
      return { label, value: ordinal(row.rank), note: rows[1] ? `${points(gap)} pts ahead of ${rows[1].name}` : 'Leading' };
    }
    const ahead = rows[index - 1];
    return { label, value: ordinal(row.rank), note: `${points(ahead.points - row.points)} pts behind ${ahead.name}` };
  });

  protected readonly series = computed(() => {
    const source = this.teams()
      ? this.store.teams().map(team => ({ id: team.teamId, name: team.name, history: team.history }))
      : this.store.players().map(player => ({ id: player.userId, name: player.username, history: player.history }));
    return topSeries(source, this.teams() ? this.store.myTeam()?.teamId : this.session.userId());
  });

  protected readonly chartLabel = computed(() => {
    const leader = this.allRows()[0];
    return leader ? `Line chart of ${this.teams() ? 'team ' : ''}points over time. ${leader.name} leads with ${points(leader.points)}.` : 'No points yet';
  });

  /** Where the chart starts: the room's opening, or its first points if those came earlier. */
  protected readonly chartStart = computed(() => {
    const opened = toDate(this.store.standings()?.openedAt)?.getTime();
    return opened ?? null;
  });

  protected readonly since = computed(() => {
    const opened = toDate(this.store.standings()?.openedAt);
    if (!opened) return 'since the first points in this lab';
    const today = new Date().toDateString() === opened.toDateString();
    return `since the lab opened ${today ? 'at ' + clock(opened) : 'on ' + dateTime(opened)}`;
  });

  protected setView(view: ScoreView): void {
    this.store.setView(view);
  }
}

function memberCount(count: number): string {
  return `${count} ${count === 1 ? 'member' : 'members'}`;
}
