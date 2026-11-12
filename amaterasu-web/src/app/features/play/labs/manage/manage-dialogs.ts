import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { signed } from '../../../../shared/format';
import { IconComponent } from '../../../../shared/ui/icon.component';
import { TeamCredit } from '../../data/play.models';
import { LabManageApi } from './lab-manage-api.service';
import { NearMissView } from './lab-manage.models';

const MAX_REASON = 200;

/** Who gets credit and for what. With a submission, the credit is for that attempt. */
export interface CreditDialogData {
  labId: string;
  teamCredit: TeamCredit;
  submissionId?: string;
  userId: string;
  username: string;
  teamName: string | null;
  questionId: string;
  questionTitle: string;
  questionPoints: number;
  answer?: string | null;
  nearMiss?: NearMissView | null;
}

/** "Near miss: wrong letter case" for a near miss; empty otherwise. */
function suggestedReason(nearMiss: NearMissView | null | undefined): string {
  return nearMiss?.reasons.length ? `Near miss: ${nearMiss.reasons.join(', ').toLowerCase()}` : '';
}

/** Gives a player credit for a question: full points by default, or a custom amount. */
@Component({
  selector: 'app-credit-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" (submit)="submit($event)" aria-labelledby="cr-h" novalidate>
      <div class="row between"><h2 class="h2" id="cr-h">Give credit</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button></div>
      <p class="dim">Marks <strong>{{ data.questionTitle }}</strong> solved for <strong>{{ data.username }}</strong>.
        @if (data.teamCredit === 'SHARED_SOLVES' && data.teamName) {It counts for {{ data.teamName }} too, as the lab shares solves.}</p>
      @if (data.answer) {
        <div class="stack tight">
          <span class="label">Their answer</span>
          <code class="answer">{{ data.answer }}</code>
          @if (data.nearMiss; as near) {
            <span class="small"><span class="chip s-warn">Near miss</span> {{ near.reasons.join(' · ') }}</span>
          }
        </div>
      }
      <div class="form-grid">
        <label class="check"><input type="checkbox" [checked]="custom()" (change)="custom.set($any($event.target).checked)">Custom amount</label>
        @if (custom()) {
          <label class="field"><span class="label">Points</span>
            <input class="input num" type="number" min="0" max="10000" step="1" [value]="amount()" (input)="amount.set(+$any($event.target).value)"></label>
        } @else {
          <div class="field"><span class="label">Points</span><span class="num">{{ data.questionPoints }} (the question's)</span></div>
        }
      </div>
      <label class="field"><span class="label">Reason</span>
        <input class="input" [class.err]="tried() && !reason().trim()" [value]="reason()" [attr.maxlength]="maxReason"
               (input)="reason.set($any($event.target).value)" placeholder="Shown in their points history and the audit log"></label>
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!valid() || busy()">
          @if (busy()) {<app-icon name="spinner" size="sm" [spin]="true" />}Give {{ points() }} pts</button>
      </div>
    </form>
  `,
  styles: `.modal { max-width: none; border: 0; box-shadow: none; } .answer { font-family: "IBM Plex Mono", ui-monospace, monospace; word-break: break-all; }`,
})
export class CreditDialogComponent {
  protected readonly data = inject<CreditDialogData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<CreditDialogComponent, string>>(MatDialogRef);
  private readonly api = inject(LabManageApi);

  protected readonly maxReason = MAX_REASON;
  protected readonly custom = signal(false);
  protected readonly amount = signal(this.data.questionPoints);
  protected readonly reason = signal(suggestedReason(this.data.nearMiss));
  protected readonly busy = signal(false);
  protected readonly tried = signal(false);
  protected readonly error = signal('');
  protected readonly points = computed(() => (this.custom() ? this.amount() : this.data.questionPoints));
  protected readonly valid = computed(() => Number.isInteger(this.points()) && this.points() >= 0 && this.points() <= 10000
    && !!this.reason().trim());

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    this.tried.set(true);
    if (!this.valid()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.credit(this.data.labId, {
        ...(this.data.submissionId ? { submissionId: this.data.submissionId } : { userId: this.data.userId, questionId: this.data.questionId }),
        points: this.custom() ? this.amount() : null,
        reason: this.reason().trim(),
      }));
      this.ref.close(result.message);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Credit could not be given.');
    } finally {
      this.busy.set(false);
    }
  }
}

export interface AcceptDialogData {
  labId: string;
  questionId: string;
  questionTitle: string;
  answer: string;
  submissionId?: string;
  /** How many players gave this answer, when known. */
  players?: number;
  nearMiss?: NearMissView | null;
}

/** Adds an answer as another accepted flag, optionally crediting everyone who already gave it. */
@Component({
  selector: 'app-accept-answer-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" (submit)="submit($event)" aria-labelledby="ac-h" novalidate>
      <div class="row between"><h2 class="h2" id="ac-h">Accept this answer</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button></div>
      <p class="dim">Adds it as another flag on <strong>{{ data.questionTitle }}</strong>, so anyone who gives it from now on solves the question.</p>
      <label class="field"><span class="label">Answer</span>
        <input class="input mono" [value]="answer()" (input)="answer.set($any($event.target).value)" maxlength="1000"></label>
      @if (data.nearMiss; as near) {
        <span class="small"><span class="chip s-warn">Near miss</span> of <code class="mono">{{ near.flag }}</code>: {{ near.reasons.join(' · ') }}</span>
      }
      <label class="check"><input type="checkbox" [checked]="caseSensitive()" (change)="caseSensitive.set($any($event.target).checked)">Letter case must match</label>
      <label class="check"><input type="checkbox" [checked]="creditExisting()" (change)="creditExisting.set($any($event.target).checked)">
        Credit everyone who already gave it{{ data.players ? ' (' + data.players + (data.players === 1 ? ' player' : ' players') + ')' : '' }}</label>
      <label class="field"><span class="label">Reason</span>
        <input class="input" [class.err]="tried() && !reason().trim()" [value]="reason()" [attr.maxlength]="maxReason"
               (input)="reason.set($any($event.target).value)" placeholder="For the audit log and, when credited, their points history"></label>
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!answer().trim() || !reason().trim() || busy()">
          @if (busy()) {<app-icon name="spinner" size="sm" [spin]="true" />}Accept answer</button>
      </div>
    </form>
  `,
  styles: `.modal { max-width: none; border: 0; box-shadow: none; }`,
})
export class AcceptAnswerDialogComponent {
  protected readonly data = inject<AcceptDialogData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<AcceptAnswerDialogComponent, string>>(MatDialogRef);
  private readonly api = inject(LabManageApi);

  protected readonly maxReason = MAX_REASON;
  protected readonly answer = signal(this.data.answer.trim());
  protected readonly caseSensitive = signal(true);
  protected readonly creditExisting = signal(true);
  protected readonly reason = signal('');
  protected readonly busy = signal(false);
  protected readonly tried = signal(false);
  protected readonly error = signal('');

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    this.tried.set(true);
    if (!this.answer().trim() || !this.reason().trim()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.acceptAnswer(this.data.labId, {
        questionId: this.data.questionId,
        answer: this.answer().trim(),
        caseSensitive: this.caseSensitive(),
        creditExisting: this.creditExisting(),
        reason: this.reason().trim(),
      }));
      this.ref.close(result.message);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The answer could not be accepted.');
    } finally {
      this.busy.set(false);
    }
  }
}

/** Resets one question for a player, or their whole lab when no question is given. */
export interface ResetDialogData {
  labId: string;
  userId: string;
  username: string;
  questionId?: string | null;
  questionTitle?: string | null;
  /** What the reset gives back or takes away, when known. */
  pointsChange?: number | null;
}

@Component({
  selector: 'app-reset-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" (submit)="submit($event)" aria-labelledby="rs-h" novalidate>
      <div class="row between"><h2 class="h2" id="rs-h">{{ title }}</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button></div>
      <div class="banner warn"><app-icon name="alert" /><div>
        @if (data.questionId) {
          {{ data.username }}'s answers, attempts and hints on {{ data.questionTitle }} are removed, with the points they brought
          or cost{{ data.pointsChange ? ' (' + fmt(data.pointsChange) + ' pts)' : '' }}. They can answer it again.
        } @else {
          Every answer, attempt and hint {{ data.username }} has in this lab is removed and their points go to 0. This cannot be undone.
        }
      </div></div>
      <label class="field"><span class="label">Reason</span>
        <input class="input" [value]="reason()" [attr.maxlength]="maxReason" (input)="reason.set($any($event.target).value)"
               placeholder="Shown in their points history and the audit log"></label>
      @if (!data.questionId) {
        <label class="field"><span class="label">Type {{ data.username }} to confirm</span>
          <input class="input" [value]="typed()" (input)="typed.set($any($event.target).value)" autocomplete="off"></label>
      }
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn danger solid" [disabled]="!ready() || busy()">
          @if (busy()) {<app-icon name="spinner" size="sm" [spin]="true" />}{{ data.questionId ? 'Reset question' : 'Reset the whole lab' }}</button>
      </div>
    </form>
  `,
  styles: `.modal { max-width: none; border: 0; box-shadow: none; }`,
})
export class ResetDialogComponent {
  protected readonly data = inject<ResetDialogData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<ResetDialogComponent, string>>(MatDialogRef);
  private readonly api = inject(LabManageApi);

  protected readonly maxReason = MAX_REASON;
  protected readonly title = this.data.questionId ? `Reset ${this.data.questionTitle} for ${this.data.username}?`
    : `Reset ${this.data.username}'s progress?`;
  protected readonly reason = signal('');
  protected readonly typed = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly fmt = signed;
  protected readonly ready = computed(() => !!this.reason().trim()
    && (!!this.data.questionId || this.typed().trim() === this.data.username));

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.ready()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.reset(this.data.labId, {
        userId: this.data.userId, questionId: this.data.questionId ?? null, reason: this.reason().trim(),
      }));
      this.ref.close(result.message);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Progress could not be reset.');
    } finally {
      this.busy.set(false);
    }
  }
}
