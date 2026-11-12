import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { roomActivity } from '../../../../core/room-activity';
import { WebsocketService } from '../../../../services/websocket.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { RoomPlayStore } from '../../rooms/room-play.store';
import { LabManageApi } from './lab-manage-api.service';
import { ManageActions, ManageTab } from './manage-actions.service';
import { ManageActivityComponent } from './manage-activity.component';
import { ManageOverviewComponent } from './manage-overview.component';
import { ManagePlayersComponent } from './manage-players.component';
import { ManageQuestionsComponent } from './manage-questions.component';
import { ManageSubmissionsComponent } from './manage-submissions.component';

const TABS: { value: ManageTab; label: string }[] = [
  { value: 'overview', label: 'Overview' },
  { value: 'submissions', label: 'Submissions' },
  { value: 'players', label: 'Players' },
  { value: 'questions', label: 'Questions' },
  { value: 'activity', label: 'Activity' },
];

/**
 * Managing one lab, for admins and its facilitators: the overview, every submission with near misses,
 * players and teams, question health and the lab's activity, with credit, accepted answers, awards and
 * resets. Scores update live over the websocket. Fits a narrow side pane and a full page.
 */
@Component({
  selector: 'app-lab-manage-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, ManageOverviewComponent, ManageSubmissionsComponent, ManagePlayersComponent,
    ManageQuestionsComponent, ManageActivityComponent],
  providers: [ManageActions],
  template: `
    @if (denied() || lab?.overview()?.room?.canRun === false) {
      <section class="card"><app-empty-state icon="lock" title="Only the lab's facilitators and admins manage it" [message]="denied()!" /></section>
    } @else {
      <div class="stack">
        <header class="head">
          <div class="row"><h2 class="h2">Manage</h2><span class="chip s-active"><span class="dot pulse"></span>Live</span></div>
          @if (actions.roomName()) {<p class="small muted">{{ actions.roomName() }}</p>}
        </header>
        <div class="tabs" role="tablist" aria-label="Manage the lab">
          @for (option of tabs; track option.value) {
            <button type="button" class="tab" role="tab" [id]="'mt-' + option.value" [class.on]="actions.tab() === option.value"
                    [attr.aria-selected]="actions.tab() === option.value" [attr.aria-controls]="'mp-' + option.value"
                    (click)="pick(option.value)">{{ option.label }}</button>
          }
        </div>
        <div role="tabpanel" [id]="'mp-' + actions.tab()" [attr.aria-labelledby]="'mt-' + actions.tab()">
          @switch (actions.tab()) {
            @case ('overview') { <app-manage-overview /> }
            @case ('submissions') { <app-manage-submissions /> }
            @case ('players') { <app-manage-players /> }
            @case ('questions') { <app-manage-questions /> }
            @case ('activity') { <app-manage-activity /> }
          }
        </div>
      </div>
    }
  `,
  styles: `
    :host { display: block; container-type: inline-size; }
    .head .h2 { margin: 0; }
    .head p { margin: 4px 0 0; }
    .tabs { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: thin; }
    .tab { flex: none; }
  `,
})
export class LabManagePage {
  private readonly api = inject(LabManageApi);
  protected readonly actions = inject(ManageActions);
  /** The lab page around this one, when there is one; it knows whether the viewer runs the lab. */
  protected readonly lab = inject(RoomPlayStore, { optional: true });

  /** The lab (room) id, from the route. */
  readonly labId = input.required<string>();
  /** ?tab= opens a tab: overview, submissions, players, questions or activity. */
  readonly tab = input<string>();

  protected readonly tabs = TABS;
  protected readonly denied = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => {
        this.actions.labId.set(id);
        this.actions.submissionFilter.set({});
        this.loadLab(id);
      });
    });
    effect(() => {
      const wanted = TABS.find(option => option.value === this.tab())?.value;
      if (wanted) untracked(() => this.actions.tab.set(wanted));
    });
    roomActivity(inject(WebsocketService).getMessage(), roomId => roomId === this.labId())
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.actions.changed());
  }

  protected pick(tab: ManageTab): void {
    if (tab === 'submissions' && this.actions.tab() !== 'submissions') this.actions.submissionFilter.set({});
    this.actions.tab.set(tab);
  }

  /** The lab's name and credit mode for the dialogs; a 403 means the caller does not run this lab. */
  private async loadLab(id: string): Promise<void> {
    this.denied.set(null);
    try {
      const teams = await firstValueFrom(this.api.teams(id));
      if (id !== this.labId()) return;
      this.actions.roomName.set(teams.roomName);
      this.actions.teamCredit.set(teams.teamCredit);
    } catch (error) {
      if ((error as { status?: number }).status === 403) {
        this.denied.set('Ask an admin to add you as a facilitator of this lab.');
      }
    }
  }
}
