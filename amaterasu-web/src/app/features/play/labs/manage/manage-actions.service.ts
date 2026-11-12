import { Injectable, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { signed } from '../../../../shared/format';
import { ToastService } from '../../../../shared/services/toast.service';
import { DIALOG_DEFAULTS } from '../../../../shared/ui/dialog';
import { AwardDialogComponent, AwardDialogData, AwardResult } from '../../../run/live/award-dialog.component';
import { TeamCredit } from '../../data/play.models';
import { ManagePlayers, SubmissionQuery } from './lab-manage.models';
import {
  AcceptAnswerDialogComponent, AcceptDialogData, CreditDialogComponent, CreditDialogData, ResetDialogComponent, ResetDialogData,
} from './manage-dialogs';

export type ManageTab = 'overview' | 'submissions' | 'players' | 'questions' | 'activity';

/**
 * The lab management page's shared state and actions, provided by the page: the lab, the open tab,
 * a filter handed to the Submissions tab, and the dialogs that change scores. A change bumps
 * {@link version}, so every tab reloads.
 */
@Injectable()
export class ManageActions {
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  readonly labId = signal('');
  readonly roomName = signal('');
  readonly teamCredit = signal<TeamCredit>('SEE_TEAMMATES');
  readonly tab = signal<ManageTab>('overview');
  /** Set when another tab opens Submissions for one player, team or question. */
  readonly submissionFilter = signal<SubmissionQuery>({});
  /** Goes up after every change made here or seen over the socket. */
  readonly version = signal(0);

  changed(): void {
    this.version.update(version => version + 1);
  }

  showSubmissions(filter: SubmissionQuery): void {
    this.submissionFilter.set(filter);
    this.tab.set('submissions');
  }

  async credit(data: Omit<CreditDialogData, 'labId' | 'teamCredit'>): Promise<void> {
    await this.open(CreditDialogComponent, { ...data, labId: this.labId(), teamCredit: this.teamCredit() });
  }

  async accept(data: Omit<AcceptDialogData, 'labId'>): Promise<void> {
    await this.open(AcceptAnswerDialogComponent, { ...data, labId: this.labId() });
  }

  async reset(data: Omit<ResetDialogData, 'labId'>): Promise<void> {
    await this.open(ResetDialogComponent, { ...data, labId: this.labId() });
  }

  /** Points for a player or a team, picked in the dialog; {@code pick} preselects one. */
  async award(players: ManagePlayers, pick: { userId?: string; teamId?: string } = {}): Promise<void> {
    const data: AwardDialogData = {
      roomId: this.labId(),
      roomName: this.roomName(),
      players: players.players.map(player => ({ userId: player.userId, username: player.username, teamName: player.teamName,
        points: player.points })),
      teams: players.teams.map(team => ({ teamId: team.teamId, name: team.name, points: team.points })),
      ...pick,
    };
    const result = await firstValueFrom(this.dialog.open<AwardDialogComponent, AwardDialogData, AwardResult>(AwardDialogComponent, {
      ...DIALOG_DEFAULTS, width: '520px', data,
    }).afterClosed());
    if (result) {
      this.toast.success(`${signed(result.points)} pts to ${result.username}`);
      this.changed();
    }
  }

  private async open<D>(component: new (...args: never[]) => unknown, data: D): Promise<void> {
    const message = await firstValueFrom(this.dialog.open<unknown, D, string>(component as never, {
      ...DIALOG_DEFAULTS, width: '520px', data,
    }).afterClosed());
    if (message) {
      this.toast.success(message);
      this.changed();
    }
  }
}
