import { TitleCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { SessionStore } from '../../../core/stores/session.store';
import { ago, clock, dateTime } from '../../../shared/format';
import { ToastService } from '../../../shared/services/toast.service';
import { IconComponent } from '../../../shared/ui/icon.component';
import { ChallengeView, HintView } from '../data/play.models';
import { RoomPlayStore } from './room-play.store';

/** One challenge: what to do, the flag form with attempts, and hints that cost points. */
@Component({
  selector: 'app-challenge-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TitleCasePipe],
  templateUrl: './challenge-detail.component.html',
  styles: `
    :host { display: block; }
    .title { font-size: 22px; }
    .desc { margin: 0; font-size: 15px; line-height: 1.6; max-width: 68ch; white-space: pre-line; }
    .flag-row { flex-wrap: nowrap; align-items: stretch; }
    .flag-input { font-family: var(--font-mono); }
    .hint { padding: 14px 16px; background: var(--surface-2); }
    .hint-row { flex-wrap: nowrap; align-items: flex-start; gap: 14px; }
    .hint-icon { margin-top: 1px; color: var(--warn); }
    .hint-text { margin-top: 4px; }
    .attempt-bar > span { background: var(--warn); }
  `,
})
export class ChallengeDetailComponent {
  protected readonly store = inject(RoomPlayStore);
  private readonly toast = inject(ToastService);
  private readonly session = inject(SessionStore);

  readonly challenge = input.required<ChallengeView>();
  readonly joined = input(false);

  protected readonly answer = signal('');
  protected readonly confirmingHint = signal<string | null>(null);
  protected readonly fmt = { ago, clock, dateTime };

  protected readonly outOfAttempts = computed(() => {
    const challenge = this.challenge();
    return !challenge.solved && !!challenge.maxAttempts && challenge.attempts >= challenge.maxAttempts;
  });
  protected readonly canAnswer = computed(() =>
    this.joined() && this.challenge().state === 'OPEN' && !this.challenge().solved && !this.outOfAttempts());
  protected readonly attemptsLabel = computed(() => {
    const { attempts, maxAttempts } = this.challenge();
    return maxAttempts ? `${attempts} of ${maxAttempts} attempts used` : `${attempts} ${attempts === 1 ? 'attempt' : 'attempts'} so far`;
  });
  protected readonly attemptPct = computed(() => {
    const { attempts, maxAttempts } = this.challenge();
    return maxAttempts ? Math.min(100, Math.round((attempts / maxAttempts) * 100)) : 0;
  });
  protected readonly leftLabel = computed(() => {
    const { attempts, maxAttempts } = this.challenge();
    if (!maxAttempts) return '';
    const left = Math.max(0, maxAttempts - attempts);
    return `${left} ${left === 1 ? 'attempt' : 'attempts'} left.`;
  });
  protected readonly wrong = computed(() => this.store.wrongFor() === this.challenge().id);

  constructor() {
    effect(() => {
      this.challenge().id;
      untracked(() => {
        this.answer.set('');
        this.confirmingHint.set(null);
      });
    });
  }

  protected hintGated(hint: HintView): boolean {
    return !hint.unlocked && hint.unlockAfterAttempts > this.challenge().attempts;
  }

  protected hintReady(hint: HintView): boolean {
    return !hint.unlocked && !this.hintGated(hint) && this.joined() && this.challenge().state === 'OPEN' && !this.challenge().solved;
  }

  protected onAnswer(value: string): void {
    this.answer.set(value);
    this.store.clearAnswerError();
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    const challenge = this.challenge();
    const outcome = await this.store.submit(challenge, this.answer());
    if (outcome?.correct) {
      this.answer.set('');
      this.toast.success(`Correct. +${outcome.points} pts to ${this.session.username()}`);
    }
  }

  protected async unlock(hint: HintView): Promise<void> {
    this.confirmingHint.set(null);
    const error = await this.store.unlockHint(this.challenge(), hint);
    if (error) {
      this.toast.show(error, 'bad', 6000);
    } else {
      this.toast.success(`Hint ${hint.number} unlocked. -${hint.cost} pts`);
    }
  }
}
