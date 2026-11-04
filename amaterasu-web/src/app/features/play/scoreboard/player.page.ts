import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CrumbsService } from '../../../core/crumbs.service';
import { SessionStore } from '../../../core/stores/session.store';
import { clock, initials, ordinal, points, signed } from '../../../shared/format';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { ChartSeries, PointsChartComponent } from './points-chart.component';
import { ScoreboardStore } from './scoreboard.store';

/** One player's standing in a room: points over time, solves by category and how they got there. */
@Component({
  selector: 'app-player-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent, PointsChartComponent],
  providers: [ScoreboardStore],
  templateUrl: './player.page.html',
  styles: `
    .who { gap: 14px; flex-wrap: nowrap; }
    .categories .meter-row { grid-template-columns: minmax(80px, 160px) minmax(0, 1fr) 40px; }
  `,
  host: { class: 'content' },
})
export class PlayerPage {
  protected readonly store = inject(ScoreboardStore);
  private readonly session = inject(SessionStore);
  private readonly crumbs = inject(CrumbsService);

  readonly roomId = input.required<string>();
  readonly username = input.required<string>();

  protected readonly fmt = { ordinal, points, signed, initials, clock };

  constructor() {
    effect(() => {
      const id = this.roomId();
      untracked(() => this.store.load(id));
    });
    effect(() => {
      const name = this.store.standings()?.roomName;
      if (name) {
        untracked(() => this.crumbs.set([
          { label: name, link: ['/rooms', this.roomId()] },
          { label: 'Scoreboard', link: ['/rooms', this.roomId(), 'scoreboard'] },
          { label: this.username() },
        ]));
      }
    });
  }

  protected readonly player = computed(() => this.store.players().find(player => player.username === this.username()));
  protected readonly isYou = computed(() => this.player()?.userId === this.session.userId());

  protected readonly categories = computed(() => {
    const entries = Object.entries(this.player()?.solvesByCategory ?? {});
    const max = Math.max(1, ...entries.map(([, count]) => count));
    return entries.sort((a, b) => b[1] - a[1]).map(([name, count]) => ({ name, count, pct: Math.round((count / max) * 100) }));
  });

  protected readonly series = computed<ChartSeries[]>(() => {
    const player = this.player();
    if (!player) return [];
    const toPoints = (history: { at: string; total: number }[]) =>
      history.map(point => ({ at: new Date(point.at).getTime(), total: point.total })).filter(point => !Number.isNaN(point.at));
    const lines: ChartSeries[] = [{ name: player.username, tone: 'you', points: toPoints(player.history) }];
    const leader = this.store.players()[0];
    if (leader && leader.userId !== player.userId) {
      lines.push({ name: leader.username, tone: 'o1', points: toPoints(leader.history) });
    }
    return lines;
  });
}
