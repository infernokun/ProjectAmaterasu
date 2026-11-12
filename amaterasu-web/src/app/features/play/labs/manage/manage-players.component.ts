import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { ago, dateTime, points, signed } from '../../../../shared/format';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { LabManageApi } from './lab-manage-api.service';
import { ManagePlayers, PlayerDetail, PlayerRow, QuestionProgress, Submission } from './lab-manage.models';
import { ManageActions } from './manage-actions.service';

/** Players and teams with their solves, attempts, hints and points; a player opens to every question and their history. */
@Component({
  selector: 'app-manage-players',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, IconComponent, SkeletonComponent],
  template: `
    @if (detail(); as d) {
      <div class="stack">
        <div class="row between">
          <button type="button" class="btn sm ghost" (click)="close()"><app-icon name="close" size="sm" />All players</button>
          <div class="row">
            <button type="button" class="btn sm" (click)="award(d.player)"><app-icon name="award" size="sm" />Award points</button>
            <button type="button" class="btn sm danger" (click)="resetAll(d.player)">Reset lab</button>
          </div>
        </div>
        <section class="card stack tight">
          <div class="row"><h3 class="h3 grow">{{ d.player.displayName || d.player.username }}
            @if (d.player.displayName) {<span class="small muted"> {{ d.player.username }}</span>}</h3>
            <span class="num strong">{{ fmt.points(d.player.points) }} pts</span></div>
          <div class="small dim">{{ d.player.teamName ? 'On ' + d.player.teamName : 'No team in this lab' }} · {{ d.player.solves }} solved ·
            {{ d.player.attempts }} attempts ({{ d.player.wrongAttempts }} wrong) · {{ d.player.hintsUsed }} hints
            @if (d.player.lastActivityAt) {· last active {{ fmt.ago(d.player.lastActivityAt) }}}</div>
        </section>

        <section class="card flush" aria-label="Questions">
          <div class="table-wrap">
            <table class="t resp">
              <thead><tr><th>Question</th><th>Status</th><th class="r">Attempts</th><th class="r">Hints</th><th class="r">Score</th><th class="r"><span class="visually-hidden">Actions</span></th></tr></thead>
              <tbody>
                @for (question of d.questions; track question.questionId) {
                  <tr>
                    <td class="lead">{{ question.title }} <span class="small muted">{{ question.points }} pts</span></td>
                    <td data-label="Status"><span>
                      @if (question.solved) {
                        <span class="chip" [class]="question.creditedBy ? 'chip s-accent' : 'chip s-active'">{{ question.creditedBy ? 'Credited by ' + question.creditedBy : 'Solved' }}</span>
                        <span class="small muted"> {{ fmt.dateTime(question.solvedAt) }}</span>
                      } @else if (question.solvedByTeammate) {
                        <span class="chip s-active">Solved by {{ question.solvedByTeammate }}</span>
                      } @else if (question.attempts) {
                        <span class="chip s-off">Not solved</span>
                      } @else {
                        <span class="small muted">Not tried</span>
                      }
                    </span></td>
                    <td data-label="Attempts" class="r num">{{ question.attempts }}</td>
                    <td data-label="Hints" class="r num">{{ question.hintsUsed }}{{ question.hintPoints ? ' (' + fmt.signed(-question.hintPoints) + ')' : '' }}</td>
                    <td data-label="Score" class="r num">{{ question.score }}</td>
                    <td class="acts r"><div class="row">
                      @if (!question.solved && !question.solvedByTeammate) {
                        <button type="button" class="btn sm" (click)="credit(d.player, question)">Give credit</button>
                      }
                      @if (question.attempts || question.solved || question.hintsUsed) {
                        <button type="button" class="btn sm ghost" (click)="resetQuestion(d.player, question)">Reset</button>
                      }
                    </div></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <section class="card flush" aria-labelledby="pl-subs">
          <div class="card-head"><h3 class="h3" id="pl-subs">Attempts</h3>
            <button type="button" class="btn sm ghost" (click)="actions.showSubmissions({ userId: d.player.userId })">In Submissions</button></div>
          <div class="feed">
            @for (item of d.submissions.slice(0, 20); track item.id) {
              <div class="feed-item" [class.near-card]="!!item.nearMiss">
                <div class="grow stack tight">
                  <div><code class="answer">{{ item.flag }}</code> <span class="small muted">on {{ item.questionTitle }} · {{ fmt.dateTime(item.at) }}</span></div>
                  @if (item.nearMiss; as near) {<div class="small"><span class="chip s-warn">Near miss</span> {{ near.reasons.join(' · ') }}</div>}
                </div>
                @if (item.creditedBy) {
                  <span class="chip s-accent">Credited</span>
                } @else if (item.correct) {
                  <span class="chip s-active">Correct</span>
                } @else if (!item.questionSolved) {
                  <button type="button" class="btn sm" [class.primary]="!!item.nearMiss" (click)="creditAttempt(item)">Give credit</button>
                }
              </div>
            } @empty {
              <div class="empty">No attempts yet.</div>
            }
          </div>
        </section>

        <section class="card flush" aria-labelledby="pl-hist">
          <div class="card-head"><h3 class="h3" id="pl-hist">Points history</h3></div>
          <div class="feed">
            @for (entry of history(d); track $index) {
              <div class="feed-item">
                <div class="grow"><div>{{ entry.reason }}</div>
                  <div class="small muted">{{ fmt.dateTime(entry.timestamp) }}{{ entry.awardedBy ? ' · by ' + entry.awardedBy : '' }}</div></div>
                <span class="num small strong">{{ fmt.signed(entry.pointsChange) }}</span>
              </div>
            } @empty {
              <div class="empty">No points yet.</div>
            }
          </div>
        </section>
      </div>
    } @else if (list(); as l) {
      <div class="stack">
        <div class="row between">
          <span class="small muted">{{ l.players.length }} {{ l.players.length === 1 ? 'player' : 'players' }}</span>
          <button type="button" class="btn sm" [disabled]="!l.players.length && !l.teams.length" (click)="actions.award(l)"><app-icon name="award" size="sm" />Award points</button>
        </div>
        <section class="card flush" aria-label="Players">
          @if (l.players.length) {
            <div class="table-wrap">
              <table class="t resp">
                <thead><tr><th>Player</th><th>Team</th><th class="r">Points</th><th class="r">Solved</th><th class="r">Attempts</th><th class="r">Hints</th><th>Last active</th></tr></thead>
                <tbody>
                  @for (player of l.players; track player.userId) {
                    <tr class="click" tabindex="0" (click)="open(player)" (keydown.enter)="open(player)">
                      <td class="lead"><span class="link">{{ player.displayName || player.username }}</span></td>
                      <td data-label="Team">{{ player.teamName ?? '—' }}</td>
                      <td data-label="Points" class="r num">{{ fmt.points(player.points) }}</td>
                      <td data-label="Solved" class="r num">{{ player.solves }}</td>
                      <td data-label="Attempts" class="r num"><span>{{ player.attempts }} <span class="small muted">({{ player.wrongAttempts }} wrong)</span></span></td>
                      <td data-label="Hints" class="r num">{{ player.hintsUsed }}</td>
                      <td data-label="Last active" class="small dim">{{ fmt.ago(player.lastActivityAt) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <app-empty-state icon="users" title="Nobody has played yet" message="Players show here after their first answer or hint." />
          }
        </section>
        @if (l.teams.length) {
          <section class="card flush" aria-labelledby="pl-teams">
            <div class="card-head"><h3 class="h3" id="pl-teams">Teams</h3></div>
            <div class="table-wrap">
              <table class="t resp">
                <thead><tr><th>Team</th><th class="r">Points</th><th class="r">Solved</th><th class="r">Attempts</th><th class="r">Hints</th><th class="r"><span class="visually-hidden">Actions</span></th></tr></thead>
                <tbody>
                  @for (team of l.teams; track team.teamId) {
                    <tr>
                      <td class="lead">{{ team.name }} <span class="small muted">{{ team.members.join(', ') }}</span></td>
                      <td data-label="Points" class="r num"><span>{{ fmt.points(team.points) }}@if (team.awardPoints) {<span class="small muted"> ({{ fmt.signed(team.awardPoints) }} to the team)</span>}</span></td>
                      <td data-label="Solved" class="r num">{{ team.solves }}</td>
                      <td data-label="Attempts" class="r num">{{ team.attempts }}</td>
                      <td data-label="Hints" class="r num">{{ team.hintsUsed }}</td>
                      <td class="acts r"><div class="row">
                        <button type="button" class="btn sm ghost" (click)="actions.award(l, { teamId: team.teamId })">Award</button>
                        <button type="button" class="btn sm ghost" (click)="actions.showSubmissions({ teamId: team.teamId })">Attempts</button>
                      </div></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }
      </div>
    } @else if (error()) {
      <section class="card"><app-empty-state icon="alert" title="Players could not be loaded" [message]="error()!" /></section>
    } @else {
      <app-skeleton variant="rows" [count]="6" label="Loading players" />
    }
  `,
  styleUrls: ['./manage-shared.scss'],
  styles: `
    tr.click { cursor: pointer; }
    tr.click:hover td { background: var(--surface-2); }
    tr.click:focus-visible { outline: 2px solid var(--focus); outline-offset: -2px; }
    .link { color: var(--text); font-weight: 500; }
    .strong { font-weight: 600; }
  `,
})
export class ManagePlayersComponent {
  private readonly api = inject(LabManageApi);
  protected readonly actions = inject(ManageActions);

  protected readonly fmt = { ago, dateTime, points, signed };
  protected readonly list = signal<ManagePlayers | null>(null);
  protected readonly detail = signal<PlayerDetail | null>(null);
  protected readonly openId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.actions.labId();
      const open = this.openId();
      this.actions.version();
      if (id) untracked(() => this.load(id, open));
    });
  }

  protected open(player: PlayerRow): void {
    this.openId.set(player.userId);
  }

  protected close(): void {
    this.openId.set(null);
    this.detail.set(null);
  }

  protected history(detail: PlayerDetail) {
    return [...detail.history].reverse();
  }

  protected award(player: PlayerRow): void {
    const list = this.list() ?? { players: [player], teams: [] };
    this.actions.award(list.players.some(row => row.userId === player.userId) ? list : { ...list, players: [player, ...list.players] },
      { userId: player.userId });
  }

  protected credit(player: PlayerRow, question: QuestionProgress): void {
    this.actions.credit({
      userId: player.userId, username: player.username, teamName: player.teamName, questionId: question.questionId,
      questionTitle: question.title, questionPoints: question.points,
    });
  }

  protected creditAttempt(item: Submission): void {
    this.actions.credit({
      submissionId: item.id, userId: item.userId, username: item.username, teamName: item.teamName, questionId: item.questionId,
      questionTitle: item.questionTitle, questionPoints: item.questionPoints, answer: item.flag, nearMiss: item.nearMiss,
    });
  }

  protected resetQuestion(player: PlayerRow, question: QuestionProgress): void {
    this.actions.reset({
      userId: player.userId, username: player.username, questionId: question.questionId, questionTitle: question.title,
      pointsChange: -question.score + question.hintPoints,
    });
  }

  protected resetAll(player: PlayerRow): void {
    this.actions.reset({ userId: player.userId, username: player.username, pointsChange: -player.points });
  }

  private async load(id: string, open: string | null): Promise<void> {
    try {
      if (open) {
        const [detail, list] = await Promise.all([firstValueFrom(this.api.player(id, open)), firstValueFrom(this.api.players(id))]);
        if (this.openId() === open) this.detail.set(detail);
        this.list.set(list);
      } else {
        this.list.set(await firstValueFrom(this.api.players(id)));
      }
      this.error.set(null);
    } catch (error) {
      if (open) {
        // Reset away or gone: back to the list
        this.close();
      }
      if (!this.list()) this.error.set(errorMessage(error) ?? 'Check your connection and try again.');
    }
  }
}
