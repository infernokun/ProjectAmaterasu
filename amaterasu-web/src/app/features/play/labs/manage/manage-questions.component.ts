import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { SessionStore } from '../../../../core/stores/session.store';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { LabManageApi } from './lab-manage-api.service';
import { QuestionStats, WrongAnswer } from './lab-manage.models';
import { ManageActions } from './manage-actions.service';

/** Each question's attempts, solve rate and most common wrong answers, with near misses marked and a way to accept one. */
@Component({
  selector: 'app-manage-questions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EmptyStateComponent, IconComponent, SkeletonComponent],
  template: `
    @if (questions(); as list) {
      <div class="stack">
        @for (question of list; track question.id) {
          <section class="card stack tight" [attr.aria-label]="question.title">
            <div class="row between">
              <h3 class="h3 grow">{{ question.title }} <span class="small muted">{{ question.points }} pts{{ question.category ? ' · ' + question.category : '' }}</span></h3>
              <div class="row">
                <button type="button" class="btn sm ghost" (click)="actions.showSubmissions({ questionId: question.id })">Attempts</button>
                @if (canEdit()) {
                  <a class="btn sm ghost" [routerLink]="['/build/labs', actions.labId()]" [queryParams]="{ step: 'questions', question: question.id }">
                    <app-icon name="edit" size="sm" />Edit question</a>
                }
              </div>
            </div>
            <div class="row stats small dim">
              <span>{{ question.solves }} solved</span><span>{{ question.attemptedBy }} tried</span><span>{{ question.attempts }} attempts</span>
              <span>{{ question.hintsUsed }} hints</span>
              @if (question.nearMisses) {<span class="s-warn">{{ question.nearMisses }} near {{ question.nearMisses === 1 ? 'miss' : 'misses' }}</span>}
            </div>
            <div class="row rate"><div class="bar grow" [class.warn]="question.solveRate < 20 && question.attemptedBy > 0" [class.ok]="question.solveRate >= 40">
              <span [style.width.%]="question.solveRate"></span></div><span class="small num">{{ question.solveRate }}% of players</span></div>
            @if (question.wrongAnswers.length) {
              <div class="label">Most common wrong answers</div>
              <ul class="wrong">
                @for (wrong of question.wrongAnswers; track wrong.answer) {
                  <li class="row" [class.near-card]="!!wrong.nearMiss">
                    <div class="grow stack tight">
                      <code class="answer">{{ wrong.answer }}</code>
                      @if (wrong.nearMiss; as near) {
                        <span class="small"><span class="chip s-warn">Near miss</span> {{ near.reasons.join(' · ') }}</span>
                      }
                    </div>
                    <span class="small num muted">{{ wrong.count }}× by {{ wrong.players }}</span>
                    <button type="button" class="btn sm" [class.primary]="!!wrong.nearMiss" (click)="accept(question, wrong)">Accept</button>
                  </li>
                }
              </ul>
            }
          </section>
        } @empty {
          <section class="card"><app-empty-state icon="flag" title="No questions in this lab" /></section>
        }
      </div>
    } @else if (error()) {
      <section class="card"><app-empty-state icon="alert" title="Questions could not be loaded" [message]="error()!" /></section>
    } @else {
      <app-skeleton variant="card" [count]="3" label="Loading questions" />
    }
  `,
  styleUrls: ['./manage-shared.scss'],
  styles: `
    .stats { gap: 12px; }
    .rate { flex-wrap: nowrap; }
    .rate .bar { min-width: 80px; }
    .wrong { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .wrong li { padding: 8px 10px; border-top: 1px solid var(--border); flex-wrap: nowrap; }
    @container (max-width: 420px) { .wrong li { flex-wrap: wrap; } }
  `,
})
export class ManageQuestionsComponent {
  private readonly api = inject(LabManageApi);
  protected readonly actions = inject(ManageActions);
  /** Editing questions is for authors; facilitators manage without it. */
  protected readonly canEdit = inject(SessionStore).canBuild;

  protected readonly questions = signal<QuestionStats[] | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.actions.labId();
      this.actions.version();
      if (id) untracked(() => this.load(id));
    });
  }

  protected accept(question: QuestionStats, wrong: WrongAnswer): void {
    this.actions.accept({
      questionId: question.id, questionTitle: question.title, answer: wrong.answer, players: wrong.players, nearMiss: wrong.nearMiss,
    });
  }

  private async load(id: string): Promise<void> {
    try {
      this.questions.set(await firstValueFrom(this.api.questions(id)));
      this.error.set(null);
    } catch (error) {
      if (!this.questions()) this.error.set(errorMessage(error) ?? 'Check your connection and try again.');
    }
  }
}
