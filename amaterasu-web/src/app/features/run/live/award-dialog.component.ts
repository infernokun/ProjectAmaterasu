import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PlayApiService } from '../../play/data/play-api.service';
import { PlayerRef } from '../../play/data/play.models';

export interface AwardDialogData {
  roomId: string;
  roomName: string;
  players: PlayerRef[];
}

export interface AwardResult {
  username: string;
  points: number;
}

const MAX_REASON = 200;

/** Add or take away points for one player, with the reason they will see. */
@Component({
  selector: 'app-award-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" (submit)="submit($event)" aria-labelledby="aw-h" novalidate>
      <div class="row between"><h2 class="h2" id="aw-h">Award points</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="close()"><app-icon name="close" /></button></div>
      <label class="field"><span class="label">Player</span>
        <select class="input" [value]="userId()" (change)="userId.set($any($event.target).value)">
          @for (player of data.players; track player.userId) {
            <option [value]="player.userId" [selected]="player.userId === userId()">{{ player.username }}{{ player.teamName ? ' (' + player.teamName + ')' : '' }}</option>
          }
        </select></label>
      <div class="form-grid">
        <label class="field"><span class="label">Points</span>
          <input class="input num" type="number" step="1" [value]="amount()" (input)="amount.set(+$any($event.target).value)">
          <span class="help">Use a negative number to take points away</span></label>
        <div class="field"><span class="label">Quick picks</span>
          <div class="row">
            @for (pick of [25, 50, 100]; track pick) {
              <button type="button" class="pick" [class.on]="amount() === pick" (click)="amount.set(pick)">{{ pick }}</button>
            }
          </div></div>
      </div>
      <label class="field"><span class="label">Reason</span>
        <input class="input" [class.err]="tried() && !reason().trim()" [value]="reason()" (input)="reason.set($any($event.target).value)"
               [attr.maxlength]="maxReason" placeholder="Shown to the player and in the points history"></label>
      <div class="banner"><app-icon name="award" /><div>{{ preview() }}</div></div>
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!valid() || busy()">
          @if (busy()) {<app-icon name="spinner" size="sm" [spin]="true" />}Award</button>
      </div>
    </form>
  `,
  styles: `.modal { max-width: none; border: 0; box-shadow: none; }`,
})
export class AwardDialogComponent {
  protected readonly data = inject<AwardDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<AwardDialogComponent, AwardResult>>(MatDialogRef);
  private readonly api = inject(PlayApiService);

  protected readonly maxReason = MAX_REASON;
  protected readonly userId = signal(this.data.players[0]?.userId ?? '');
  protected readonly amount = signal(50);
  protected readonly reason = signal('');
  protected readonly busy = signal(false);
  protected readonly tried = signal(false);
  protected readonly error = signal('');

  private readonly player = computed(() => this.data.players.find(player => player.userId === this.userId()));
  protected readonly valid = computed(() =>
    !!this.player() && Number.isInteger(this.amount()) && this.amount() !== 0 && Math.abs(this.amount()) <= 10000
    && !!this.reason().trim());
  protected readonly preview = computed(() => {
    const player = this.player();
    if (!player) return 'Pick a player.';
    const amount = this.amount() || 0;
    const verb = amount < 0 ? `loses ${Math.abs(amount)}` : `gets +${amount}`;
    return `${player.username} ${verb} pts in ${this.data.roomName}, going to ${(player.points + amount).toLocaleString('en-US')}. The reason shows in their points history.`;
  });

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    this.tried.set(true);
    const player = this.player();
    if (!this.valid() || !player) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.awardPoints(this.data.roomId, player.userId, this.amount(), this.reason().trim()));
      this.ref.close({ username: player.username, points: this.amount() });
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The points could not be awarded.');
    } finally {
      this.busy.set(false);
    }
  }

  protected close(): void {
    this.ref.close();
  }
}
