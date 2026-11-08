import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';
import { roomActivity } from '../../../core/room-activity';
import { SessionStore } from '../../../core/stores/session.store';
import { WebsocketService } from '../../../services/websocket.service';
import { ordinal, points } from '../../../shared/format';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { PlayApiService } from '../data/play-api.service';
import { RoomStandings } from '../data/play.models';

type View = 'people' | 'teams';

/** The lab's top players or teams, with a link to the full scoreboard. */
@Component({
  selector: 'app-lab-leaderboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EmptyStateComponent],
  template: `
    <section class="card flush" aria-labelledby="lb-h">
      <div class="card-head">
        <h2 class="h2" id="lb-h">Leaderboard</h2>
        <div class="row">
          <div class="seg" role="group" aria-label="Leaderboard">
            <button type="button" [class.on]="view() === 'people'" [attr.aria-pressed]="view() === 'people'" (click)="view.set('people')">People</button>
            <button type="button" [class.on]="view() === 'teams'" [attr.aria-pressed]="view() === 'teams'" (click)="view.set('teams')">Teams</button>
          </div>
          <a class="small link" [routerLink]="['/labs', labId(), 'scoreboard']">Full scoreboard</a>
        </div>
      </div>
      @if (rows().length) {
        <div class="table-wrap">
          <table class="t">
            <thead><tr><th class="r">Rank</th><th>{{ view() === 'teams' ? 'Team' : 'Player' }}</th><th class="r">Points</th><th class="r">Solves</th></tr></thead>
            <tbody>
              @for (row of rows(); track row.id) {
                <tr [class.you]="row.you">
                  <td class="r num">{{ fmt.ordinal(row.rank) }}</td>
                  <td><strong>{{ row.name }}</strong>@if (row.you) { <span class="muted">({{ view() === 'teams' ? 'your team' : 'you' }})</span> }
                    @if (row.sub) { <div class="small muted">{{ row.sub }}</div> }</td>
                  <td class="r num">{{ fmt.points(row.points) }}</td>
                  <td class="r num">{{ row.solves }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <app-empty-state [title]="view() === 'teams' ? 'No teams yet' : 'No players yet'"
          [message]="view() === 'teams' ? 'Teams show here once their members join and score.' : 'Players show here once they join.'" />
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    tr.you td { background: var(--accent-soft, var(--surface-2)); }
  `,
})
export class LabLeaderboardComponent {
  private readonly api = inject(PlayApiService);
  private readonly session = inject(SessionStore);

  readonly labId = input.required<string>();
  readonly limit = input(10);
  /** The viewer's team in this lab. */
  readonly myTeamId = input<string | null>(null);

  protected readonly view = signal<View>('people');
  private readonly standings = signal<RoomStandings | null>(null);
  protected readonly fmt = { ordinal, points };

  protected readonly rows = computed(() => {
    const standings = this.standings();
    if (!standings) return [];
    const rows = this.view() === 'teams'
      ? standings.teams.map(team => ({ id: team.teamId, rank: team.rank, name: team.name, points: team.points, solves: team.solves,
        sub: `${team.members} ${team.members === 1 ? 'member' : 'members'} here`, you: team.teamId === this.myTeamId() }))
      : standings.players.map(player => ({ id: player.userId, rank: player.rank, name: player.username, points: player.points,
        solves: player.solves, sub: player.teamName ?? '', you: player.userId === this.session.userId() }));
    return rows.slice(0, this.limit());
  });

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => this.load(id));
    });
    roomActivity(inject(WebsocketService).getMessage(), roomId => roomId === this.labId())
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(this.labId()));
  }

  private async load(id: string): Promise<void> {
    const standings = await firstValueFrom(this.api.standings(id).pipe(catchError(() => of(null))));
    if (id === this.labId()) this.standings.set(standings);
  }
}
