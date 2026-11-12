import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { roomActivity } from '../../../../core/room-activity';
import { SessionStore } from '../../../../core/stores/session.store';
import { WebsocketService } from '../../../../services/websocket.service';
import { initials } from '../../../../shared/format';
import { ConfirmService } from '../../../../shared/services/confirm.service';
import { ToastService } from '../../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { RoomPlayStore } from '../../rooms/room-play.store';
import { ENVIRONMENT_STATUS_META } from '../environment-status';
import { LabManageApi } from '../manage/lab-manage-api.service';
import { LabTeamView, LabTeamsView, TeamCandidate, TeamMode } from '../manage/lab-manage.models';

interface ModeOption {
  value: TeamMode;
  label: string;
  help: string;
}

export const TEAM_MODES: ModeOption[] = [
  { value: 'SITE', label: 'Site teams', help: 'The teams admins run in Users and teams. Each player\'s site team counts here.' },
  { value: 'NONE', label: 'No teams', help: 'Everyone plays solo. Per-team environments give everyone the Solo copy.' },
  { value: 'LAB', label: 'Lab teams', help: 'Teams only this lab uses. Anyone can be on one, with or without a site team.' },
];

const MAX_MATCHES = 8;

/**
 * A lab's teams for the people who run it: which teams count, and in Lab teams mode the lab's own
 * teams and who is on them. Fits a narrow side pane and a full page.
 */
@Component({
  selector: 'app-lab-teams-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    @if (notRunner()) {
      <section class="card"><app-empty-state icon="lock" title="Only the lab's facilitators and admins manage its teams"
        message="Ask an admin to add you as a facilitator of this lab." /></section>
    } @else if (view(); as v) {
      <div class="stack">
        <header class="head">
          <h2 class="h2">Teams</h2>
          <p class="small muted">Who plays together in {{ v.roomName }}: the scoreboard's Teams board,
            {{ v.teamCredit === 'SHARED_SOLVES' ? 'shared solves' : 'teammates\\' solves' }}{{ v.environmentMode === 'PER_TEAM' ? ' and each team\\'s copy of the environment' : '' }}.</p>
        </header>

        <section class="card stack tight" aria-labelledby="mode-h">
          <h3 class="h3" id="mode-h">Which teams count</h3>
          <div class="modes" role="radiogroup" aria-labelledby="mode-h">
            @for (option of modes; track option.value) {
              <button type="button" class="mode" role="radio" [class.on]="v.teamMode === option.value"
                      [attr.aria-checked]="v.teamMode === option.value" [disabled]="busy()" (click)="setMode(option.value)">
                <span class="row mode-top"><span class="dot-radio" aria-hidden="true"></span><strong>{{ option.label }}</strong></span>
                <span class="small muted">{{ option.help }}</span>
              </button>
            }
          </div>
        </section>

        @switch (v.teamMode) {
          @case ('SITE') {
            <section class="card flush" aria-labelledby="site-h">
              <div class="card-head">
                <h3 class="h3" id="site-h">Site teams</h3>
                @if (session.isAdmin()) {
                  <a class="btn sm" routerLink="/admin/users" [queryParams]="{ tab: 'teams' }"><app-icon name="users" size="sm" />Users and teams</a>
                }
              </div>
              @if (!session.isAdmin()) {
                <p class="small muted pad">Admins change site teams in Admin › Users and teams.</p>
              }
              @for (team of v.siteTeams; track team.id) {
                <div class="team-line">
                  <div class="row between"><strong>{{ team.name }}</strong><span class="tag">{{ count(team) }}</span></div>
                  @if (team.members.length) {
                    <div class="small dim names">{{ names(team) }}</div>
                  }
                </div>
              } @empty {
                <app-empty-state icon="users" title="No site teams" message="Everyone plays solo until an admin makes site teams." />
              }
            </section>
          }
          @case ('NONE') {
            <div class="banner"><app-icon name="user" /><div>Everyone plays solo in this lab. Scores are per player, and a
              per-team environment gives everyone the Solo copy.</div></div>
          }
          @case ('LAB') {
            <div class="row toolbar">
              <form class="row grow new-team" (submit)="$event.preventDefault(); create()">
                <label class="visually-hidden" for="new-team">New team name</label>
                <input id="new-team" class="input grow" placeholder="New team name" maxlength="80" [value]="newName()"
                       (input)="newName.set($any($event.target).value)">
                <button type="submit" class="btn primary" [disabled]="!newName().trim() || busy()"><app-icon name="plus" size="sm" />Add team</button>
              </form>
              <button type="button" class="btn" [disabled]="busy()" (click)="copySite()"><app-icon name="copy" size="sm" />Copy from site teams</button>
            </div>

            @for (team of v.labTeams; track team.id) {
              <article class="card stack tight team" [attr.aria-label]="team.name">
                <div class="row between">
                  @if (renaming() === team.id) {
                    <form class="row grow" (submit)="$event.preventDefault(); rename(team)">
                      <label class="visually-hidden" [for]="'rn-' + team.id">Team name</label>
                      <input class="input grow" [id]="'rn-' + team.id" maxlength="80" [value]="renameTo()"
                             (input)="renameTo.set($any($event.target).value)" (keydown.escape)="renaming.set(null)">
                      <button type="submit" class="btn sm primary" [disabled]="!renameTo().trim() || busy()">Save</button>
                      <button type="button" class="btn sm ghost" (click)="renaming.set(null)">Cancel</button>
                    </form>
                  } @else {
                    <div class="row grow">
                      <h3 class="h3">{{ team.name }}</h3>
                      <span class="tag">{{ count(team) }}</span>
                      @if (team.copyStatus; as status) {
                        <span class="chip" [class]="'chip s-' + statusMeta[status].tone">{{ statusMeta[status].label }}</span>
                      }
                    </div>
                    <div class="row">
                      <button type="button" class="btn sm ghost" (click)="startRename(team)">Rename</button>
                      <button type="button" class="btn sm ghost danger" [disabled]="busy()" (click)="remove(team)">Delete</button>
                    </div>
                  }
                </div>
                <ul class="members" [attr.aria-label]="'Members of ' + team.name">
                  @for (member of team.members; track member.userId) {
                    <li class="member">
                      <span class="avatar sm" aria-hidden="true">{{ initials(member.username) }}</span>
                      <span class="grow">{{ member.displayName || member.username }}
                        @if (member.displayName) {<span class="small muted"> {{ member.username }}</span>}</span>
                      <button type="button" class="iconbtn sm" [attr.aria-label]="'Take ' + member.username + ' off ' + team.name"
                              [disabled]="busy()" (click)="removeMember(team, member.userId, member.username)"><app-icon name="close" size="sm" /></button>
                    </li>
                  } @empty {
                    <li class="small muted">Nobody yet.</li>
                  }
                </ul>
                @if (adding() === team.id) {
                  <div class="picker stack tight">
                    <label class="search">
                      <app-icon name="search" size="sm" />
                      <input type="search" [attr.aria-label]="'Find people to add to ' + team.name" placeholder="Find people by name"
                             [value]="query()" (input)="query.set($any($event.target).value)">
                    </label>
                    <ul class="matches" role="list">
                      @for (person of matches(team); track person.userId) {
                        <li class="row match">
                          <span class="grow">{{ person.displayName || person.username }}
                            <span class="small muted">{{ person.labTeamName ? 'on ' + person.labTeamName : person.siteTeamName ? 'site team ' + person.siteTeamName : 'no team' }}{{ person.played ? '' : ' · not played yet' }}</span></span>
                          <button type="button" class="btn sm" [disabled]="busy()" (click)="addMember(team, person)">
                            {{ person.labTeamId ? 'Move here' : 'Add' }}</button>
                        </li>
                      } @empty {
                        <li class="small muted">{{ query().trim() ? 'Nobody matches.' : 'Everyone is on this team.' }}</li>
                      }
                    </ul>
                    <button type="button" class="btn sm ghost" (click)="adding.set(null)">Done</button>
                  </div>
                } @else {
                  <button type="button" class="btn sm add" (click)="openPicker(team)"><app-icon name="plus" size="sm" />Add people</button>
                }
              </article>
            } @empty {
              <section class="card">
                <app-empty-state icon="users" title="No lab teams yet"
                  message="Add a team, or copy the site teams to start from them. Players without a team play solo." />
              </section>
            }

            @if (unassigned().length) {
              <section class="card stack tight" aria-labelledby="solo-h">
                <h3 class="h3" id="solo-h">Played, but on no team here</h3>
                <p class="small dim">{{ unassignedNames() }}</p>
              </section>
            }
          }
        }

        @if (v.teamMode !== 'LAB' && v.labTeams.length) {
          <p class="small muted">This lab keeps {{ v.labTeams.length }} {{ v.labTeams.length === 1 ? 'team' : 'teams' }} of its own;
            they count again when you pick Lab teams.</p>
        }
      </div>
    } @else if (error()) {
      <section class="card"><app-empty-state icon="alert" title="Teams could not be loaded" [message]="error()!">
        <button type="button" class="btn sm" (click)="load()">Try again</button></app-empty-state></section>
    } @else {
      <app-skeleton variant="card" [count]="2" label="Loading teams" />
    }
  `,
  styles: `
    :host { display: block; container-type: inline-size; }
    .head .h2 { margin-bottom: 4px; }
    .head p { margin: 0; }
    .modes { display: grid; gap: 8px; grid-template-columns: repeat(3, minmax(0, 1fr)); }
    @container (max-width: 640px) { .modes { grid-template-columns: 1fr; } }
    .mode { display: flex; flex-direction: column; gap: 4px; text-align: left; padding: 10px 12px; border-radius: 10px;
      border: 1px solid var(--border-2); background: var(--surface-2); color: var(--text); font: inherit; cursor: pointer; }
    .mode:hover { background: var(--surface-3); }
    .mode.on { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); background: var(--accent-soft); }
    .mode:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
    .mode[disabled] { cursor: progress; }
    .mode-top { gap: 8px; flex-wrap: nowrap; }
    .dot-radio { width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--border-2); flex: none; }
    .mode.on .dot-radio { border-color: var(--accent); background: radial-gradient(var(--accent) 45%, transparent 50%); }
    .pad { padding: 0 var(--pad-card); }
    .team-line { padding: var(--pad-row) var(--pad-card); border-top: 1px solid var(--border); }
    .names { margin-top: 4px; }
    .toolbar { align-items: stretch; }
    .new-team { flex-wrap: nowrap; min-width: 240px; }
    @container (max-width: 520px) { .new-team { min-width: 0; flex-basis: 100%; } .toolbar > .btn { flex: 1 1 auto; } }
    .members { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
    .member { display: flex; align-items: center; gap: 8px; min-height: 36px; }
    .avatar.sm { width: 26px; height: 26px; font-size: 11px; }
    .iconbtn.sm { width: 30px; height: 30px; }
    .danger { color: var(--bad); }
    .add { align-self: flex-start; }
    .picker { padding: 10px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface-2); }
    .matches { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
    .match { flex-wrap: nowrap; }
    .match .grow { overflow: hidden; text-overflow: ellipsis; }
  `,
})
export class LabTeamsPage {
  private readonly api = inject(LabManageApi);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionStore);
  /** The lab page around this one, when there is one. */
  private readonly lab = inject(RoomPlayStore, { optional: true });

  /** The lab (room) id, from the route. */
  readonly labId = input.required<string>();

  protected readonly modes = TEAM_MODES;
  protected readonly statusMeta = ENVIRONMENT_STATUS_META;
  protected readonly view = signal<LabTeamsView | null>(null);
  protected readonly candidates = signal<TeamCandidate[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly newName = signal('');
  protected readonly renaming = signal<string | null>(null);
  protected readonly renameTo = signal('');
  protected readonly adding = signal<string | null>(null);
  protected readonly query = signal('');

  private readonly denied = signal(false);
  /** The lab page already knows the viewer does not run this lab; the API refuses with 403 too. */
  protected readonly notRunner = computed(() => this.denied() || this.lab?.overview()?.room.canRun === false);
  protected readonly unassigned = computed(() => this.candidates().filter(person => person.played && !person.labTeamId));
  protected readonly unassignedNames = computed(() => this.unassigned().map(person => person.username).join(', '));

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => {
        this.view.set(null);
        this.load();
      });
    });
    roomActivity(inject(WebsocketService).getMessage(), roomId => roomId === this.labId())
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(true));
  }

  async load(quiet = false): Promise<void> {
    const id = this.labId();
    if (!quiet) this.error.set(null);
    try {
      const view = await firstValueFrom(this.api.teams(id));
      if (id !== this.labId()) return;
      this.view.set(view);
      if (view.teamMode === 'LAB') {
        this.candidates.set(await firstValueFrom(this.api.candidates(id)));
      }
    } catch (error) {
      if ((error as { status?: number }).status === 403) {
        this.denied.set(true);
      } else if (!quiet) {
        this.error.set(errorMessage(error) ?? 'Check your connection and try again.');
      }
    }
  }

  protected count(team: LabTeamView): string {
    return `${team.members.length} ${team.members.length === 1 ? 'member' : 'members'}`;
  }

  protected names(team: LabTeamView): string {
    return team.members.map(member => member.username).join(', ');
  }

  protected readonly initials = initials;

  protected matches(team: LabTeamView): TeamCandidate[] {
    const term = this.query().trim().toLowerCase();
    return this.candidates()
      .filter(person => person.labTeamId !== team.id)
      .filter(person => !term || person.username.toLowerCase().includes(term) || (person.displayName ?? '').toLowerCase().includes(term))
      .sort((a, b) => Number(!!a.labTeamId) - Number(!!b.labTeamId) || Number(b.played) - Number(a.played))
      .slice(0, MAX_MATCHES);
  }

  protected async setMode(mode: TeamMode): Promise<void> {
    const current = this.view();
    if (!current || current.teamMode === mode) return;
    const option = TEAM_MODES.find(candidate => candidate.value === mode)!;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Use ${option.label.toLowerCase()} in ${current.roomName}?`,
      message: `${option.help} The scoreboard's Teams board and team environments follow at once.`,
      confirmLabel: `Use ${option.label.toLowerCase()}`,
      tone: 'primary',
      icon: 'users',
    }));
    if (answer !== 'confirm') return;
    await this.act(() => this.api.setTeamMode(this.labId(), mode), `${option.label} count in this lab`);
  }

  protected async create(): Promise<void> {
    const name = this.newName().trim();
    if (!name) return;
    if (await this.act(() => this.api.createTeam(this.labId(), name), `${name} added`)) this.newName.set('');
  }

  protected startRename(team: LabTeamView): void {
    this.renameTo.set(team.name);
    this.renaming.set(team.id);
  }

  protected async rename(team: LabTeamView): Promise<void> {
    const name = this.renameTo().trim();
    if (!name) return;
    if (await this.act(() => this.api.editTeam(this.labId(), team.id, name), `Renamed to ${name}`)) this.renaming.set(null);
  }

  protected async remove(team: LabTeamView): Promise<void> {
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${team.name}?`,
      message: team.members.length
        ? `${this.count(team)} play solo in this lab until you put them on another team. Their points stay theirs.`
        : 'The team has nobody on it.',
      confirmLabel: 'Delete team',
      icon: 'trash',
    }));
    if (answer !== 'confirm') return;
    await this.act(() => this.api.deleteTeam(this.labId(), team.id), `${team.name} deleted`);
  }

  protected openPicker(team: LabTeamView): void {
    this.query.set('');
    this.adding.set(team.id);
  }

  protected async addMember(team: LabTeamView, person: TeamCandidate): Promise<void> {
    await this.act(() => this.api.addMembers(this.labId(), team.id, [person.userId]),
      person.labTeamName ? `${person.username} moved from ${person.labTeamName} to ${team.name}` : `${person.username} added to ${team.name}`);
  }

  protected async removeMember(team: LabTeamView, userId: string, username: string): Promise<void> {
    await this.act(() => this.api.removeMember(this.labId(), team.id, userId), `${username} taken off ${team.name}`);
  }

  protected async copySite(): Promise<void> {
    this.busy.set(true);
    try {
      const result = await firstValueFrom(this.api.copySiteTeams(this.labId()));
      this.view.set(result.data);
      this.toast.success(result.message);
      await this.refreshCandidates();
    } catch (error) {
      this.toast.error('The site teams could not be copied.', error);
    } finally {
      this.busy.set(false);
    }
  }

  /** Runs a change that answers with the new team setup. */
  private async act(change: () => import('rxjs').Observable<LabTeamsView>, done: string): Promise<boolean> {
    this.busy.set(true);
    try {
      this.view.set(await firstValueFrom(change()));
      this.toast.success(done);
      await this.refreshCandidates();
      return true;
    } catch (error) {
      this.toast.error('That change was not saved.', error);
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async refreshCandidates(): Promise<void> {
    if (this.view()?.teamMode !== 'LAB') return;
    try {
      this.candidates.set(await firstValueFrom(this.api.candidates(this.labId())));
    } catch {
      // The picker keeps its last list
    }
  }
}
