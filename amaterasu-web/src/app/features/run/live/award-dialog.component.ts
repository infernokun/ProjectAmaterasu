import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PlayApiService } from '../../play/data/play-api.service';
import { PlayerRef } from '../../play/data/play.models';
import { LabManageApi } from '../../play/labs/manage/lab-manage-api.service';

export interface AwardDialogData {
  roomId: string;
  roomName: string;
  players: PlayerRef[];
  /** Teams that can be awarded as a whole; the team target shows only when given. */
  teams?: { teamId: string; name: string; points: number }[];
  /** Preselects a player, or a team. */
  userId?: string;
  teamId?: string;
}

export interface AwardResult {
  username: string;
  points: number;
}

const MAX_REASON = 200;

/** Add or take away points for one player, or a team as a whole, with the reason they will see. */
@Component({
  selector: 'app-award-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" (submit)="submit($event)" aria-labelledby="aw-h" novalidate>
      <div class="row between"><h2 class="h2" id="aw-h">Award points</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="close()"><app-icon name="close" /></button></div>
      @if (data.teams?.length) {
        <div class="seg" role="group" aria-label="Award to">
          <button type="button" [class.on]="target() === 'player'" [attr.aria-pressed]="target() === 'player'" (click)="target.set('player')">A player</button>
          <button type="button" [class.on]="target() === 'team'" [attr.aria-pressed]="target() === 'team'" (click)="target.set('team')">A team</button>
        </div>
      }
      @if (target() === 'team') {
        <label class="field"><span class="label">Team</span>
          <select class="input" [value]="teamId()" (change)="teamId.set($any($event.target).value)">
            @for (team of data.teams ?? []; track team.teamId) {
              <option [value]="team.teamId" [selected]="team.teamId === teamId()">{{ team.name }}</option>
            }
          </select>
          <span class="help">On top of its members' points, on the Teams board</span></label>
      } @else {
        <label class="field"><span class="label">Player</span>
          <select class="input" [value]="userId()" (change)="userId.set($any($event.target).value)">
            @for (player of data.players; track player.userId) {
              <option [value]="player.userId" [selected]="player.userId === userId()">{{ player.username }}{{ player.teamName ? ' (' + player.teamName + ')' : '' }}</option>
            }
          </select></label>
      }
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
  private readonly manage = inject(LabManageApi);

  protected readonly maxReason = MAX_REASON;
  protected readonly target = signal<'player' | 'team'>(this.data.teamId && this.data.teams?.length ? 'team' : 'player');
  protected readonly userId = signal(this.data.userId ?? this.data.players[0]?.userId ?? '');
  protected readonly teamId = signal(this.data.teamId ?? this.data.teams?.[0]?.teamId ?? '');
  protected readonly amount = signal(50);
  protected readonly reason = signal('');
  protected readonly busy = signal(false);
  protected readonly tried = signal(false);
  protected readonly error = signal('');

  private readonly player = computed(() => this.data.players.find(player => player.userId === this.userId()));
  private readonly team = computed(() => this.data.teams?.find(team => team.teamId === this.teamId()));
  /** Who gets the points: the picked player or team. */
  private readonly recipient = computed(() => {
    if (this.target() === 'team') {
      const team = this.team();
      return team ? { name: team.name, points: team.points } : undefined;
    }
    const player = this.player();
    return player ? { name: player.username, points: player.points } : undefined;
  });
  protected readonly valid = computed(() =>
    !!this.recipient() && Number.isInteger(this.amount()) && this.amount() !== 0 && Math.abs(this.amount()) <= 10000
    && !!this.reason().trim());
  protected readonly preview = computed(() => {
    const recipient = this.recipient();
    if (!recipient) return this.target() === 'team' ? 'Pick a team.' : 'Pick a player.';
    const amount = this.amount() || 0;
    const verb = amount < 0 ? `loses ${Math.abs(amount)}` : `gets +${amount}`;
    const where = this.target() === 'team' ? 'The reason shows in the lab\'s activity.' : 'The reason shows in their points history.';
    return `${recipient.name} ${verb} pts in ${this.data.roomName}, going to ${(recipient.points + amount).toLocaleString('en-US')}. ${where}`;
  });

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    this.tried.set(true);
    const recipient = this.recipient();
    if (!this.valid() || !recipient) return;
    this.busy.set(true);
    this.error.set('');
    try {
      if (this.target() === 'team') {
        await firstValueFrom(this.manage.award(this.data.roomId, { teamId: this.teamId(), points: this.amount(), reason: this.reason().trim() }));
      } else {
        await firstValueFrom(this.api.awardPoints(this.data.roomId, this.userId(), this.amount(), this.reason().trim()));
      }
      this.ref.close({ username: recipient.name, points: this.amount() });
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
