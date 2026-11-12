import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { dateTime } from '../../../../shared/format';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { LabManageApi } from './lab-manage-api.service';
import { ManagePlayers, QuestionStats, Submission, SubmissionPage, SubmissionQuery, SubmissionResult } from './lab-manage.models';
import { ManageActions } from './manage-actions.service';

const PAGE_SIZE = 50;

/** Every attempt, newest first, filterable by player, team, question and result. Near misses are marked with why. */
@Component({
  selector: 'app-manage-submissions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, IconComponent, SkeletonComponent],
  template: `
    <div class="stack">
      <div class="row filters" role="group" aria-label="Filter submissions">
        <label class="visually-hidden" for="sf-player">Player</label>
        <select id="sf-player" class="input" [value]="filter().userId ?? ''" (change)="set({ userId: value($event) })">
          <option value="">Every player</option>
          @for (player of people()?.players ?? []; track player.userId) {
            <option [value]="player.userId" [selected]="player.userId === filter().userId">{{ player.username }}</option>
          }
        </select>
        @if (people()?.teams?.length) {
          <label class="visually-hidden" for="sf-team">Team</label>
          <select id="sf-team" class="input" [value]="filter().teamId ?? ''" (change)="set({ teamId: value($event) })">
            <option value="">Every team</option>
            @for (team of people()!.teams; track team.teamId) {
              <option [value]="team.teamId" [selected]="team.teamId === filter().teamId">{{ team.name }}</option>
            }
            <option value="none" [selected]="filter().teamId === 'none'">No team</option>
          </select>
        }
        <label class="visually-hidden" for="sf-q">Question</label>
        <select id="sf-q" class="input" [value]="filter().questionId ?? ''" (change)="set({ questionId: value($event) })">
          <option value="">Every question</option>
          @for (question of questions(); track question.id) {
            <option [value]="question.id" [selected]="question.id === filter().questionId">{{ question.title }}</option>
          }
        </select>
        <div class="seg" role="group" aria-label="Result">
          @for (option of results; track option.value) {
            <button type="button" [class.on]="(filter().result ?? 'all') === option.value" [attr.aria-pressed]="(filter().result ?? 'all') === option.value"
                    (click)="set({ result: option.value })">{{ option.label }}</button>
          }
        </div>
      </div>

      @if (page(); as p) {
        <section class="card flush" aria-label="Submissions">
          <div class="card-head"><span class="small muted">{{ p.total }} {{ p.total === 1 ? 'attempt' : 'attempts' }}
            @if (p.nearMisses) {· <span class="s-warn">{{ p.nearMisses }} near {{ p.nearMisses === 1 ? 'miss' : 'misses' }}</span>}</span></div>
          @if (p.items.length) {
            <div class="table-wrap">
              <table class="t resp near-rows">
                <thead><tr><th>Answer</th><th>Player</th><th>Question</th><th>When</th><th>Result</th><th class="r"><span class="visually-hidden">Actions</span></th></tr></thead>
                <tbody>
                  @for (item of p.items; track item.id) {
                    <tr [class.near]="!!item.nearMiss">
                      <td class="lead"><code class="answer">{{ item.flag }}</code>
                        @if (item.nearMiss; as near) {
                          <div class="row reasons small"><span class="chip s-warn">Near miss</span>{{ near.reasons.join(' · ') }}</div>
                        }</td>
                      <td data-label="Player"><span>{{ item.username }}@if (item.teamName) {<span class="small muted"> · {{ item.teamName }}</span>}</span></td>
                      <td data-label="Question">{{ item.questionTitle }}</td>
                      <td data-label="When" class="small dim">{{ fmt.dateTime(item.at) }}</td>
                      <td data-label="Result">
                        @if (item.creditedBy) {
                          <span class="chip s-accent" [title]="'Credited by ' + item.creditedBy">Credited</span>
                        } @else if (item.correct) {
                          <span class="chip s-active">Correct</span>
                        } @else {
                          <span class="chip s-off">Wrong</span>
                        }
                      </td>
                      <td class="acts r">
                        @if (!item.correct && !item.creditedBy) {
                          <div class="row">
                            @if (!item.questionSolved) {
                              <button type="button" class="btn sm" [class.primary]="!!item.nearMiss" (click)="credit(item)">
                                <app-icon name="check" size="sm" />Give credit</button>
                            }
                            <button type="button" class="btn sm" (click)="accept(item)">Accept this answer</button>
                          </div>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (p.total > p.size) {
              <div class="row between pager">
                <button type="button" class="btn sm" [disabled]="p.page === 0" (click)="go(p.page - 1)">Newer</button>
                <span class="small muted">Page {{ p.page + 1 }} of {{ pages() }}</span>
                <button type="button" class="btn sm" [disabled]="p.page + 1 >= pages()" (click)="go(p.page + 1)">Older</button>
              </div>
            }
          } @else {
            <app-empty-state icon="flag" title="No attempts match" message="Change the filters, or wait for players to answer." />
          }
        </section>
      } @else if (error()) {
        <section class="card"><app-empty-state icon="alert" title="Submissions could not be loaded" [message]="error()!" /></section>
      } @else {
        <app-skeleton variant="rows" [count]="6" label="Loading submissions" />
      }
    </div>
  `,
  styleUrls: ['./manage-shared.scss'],
})
export class ManageSubmissionsComponent {
  private readonly api = inject(LabManageApi);
  protected readonly actions = inject(ManageActions);

  protected readonly results: { value: SubmissionResult; label: string }[] = [
    { value: 'all', label: 'All' }, { value: 'near', label: 'Near misses' }, { value: 'wrong', label: 'Wrong' }, { value: 'correct', label: 'Correct' },
  ];
  protected readonly fmt = { dateTime };
  protected readonly filter = this.actions.submissionFilter;
  protected readonly page = signal<SubmissionPage | null>(null);
  protected readonly people = signal<ManagePlayers | null>(null);
  protected readonly questions = signal<QuestionStats[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly pages = computed(() => {
    const p = this.page();
    return p ? Math.max(1, Math.ceil(p.total / p.size)) : 1;
  });

  constructor() {
    effect(() => {
      const id = this.actions.labId();
      const query = this.filter();
      this.actions.version();
      if (id) untracked(() => this.load(id, query));
    });
    effect(() => {
      const id = this.actions.labId();
      this.actions.version();
      if (id) untracked(() => this.loadOptions(id));
    });
  }

  protected value(event: Event): string | null {
    return (event.target as HTMLSelectElement).value || null;
  }

  protected set(change: SubmissionQuery): void {
    this.filter.update(current => ({ ...current, ...change, page: 0 }));
  }

  protected go(page: number): void {
    this.filter.update(current => ({ ...current, page }));
  }

  protected credit(item: Submission): void {
    this.actions.credit({
      submissionId: item.id, userId: item.userId, username: item.username, teamName: item.teamName, questionId: item.questionId,
      questionTitle: item.questionTitle, questionPoints: item.questionPoints, answer: item.flag, nearMiss: item.nearMiss,
    });
  }

  protected accept(item: Submission): void {
    this.actions.accept({
      questionId: item.questionId, questionTitle: item.questionTitle, answer: item.flag ?? '', submissionId: item.id,
      nearMiss: item.nearMiss,
    });
  }

  private async load(id: string, query: SubmissionQuery): Promise<void> {
    try {
      const page = await firstValueFrom(this.api.submissions(id, { size: PAGE_SIZE, ...query }));
      if (id === this.actions.labId()) {
        this.page.set(page);
        this.error.set(null);
      }
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Check your connection and try again.');
      this.page.set(null);
    }
  }

  private async loadOptions(id: string): Promise<void> {
    try {
      const [people, questions] = await Promise.all([firstValueFrom(this.api.players(id)), firstValueFrom(this.api.questions(id))]);
      this.people.set(people);
      this.questions.set(questions);
    } catch {
      // The filters keep their last options
    }
  }
}
