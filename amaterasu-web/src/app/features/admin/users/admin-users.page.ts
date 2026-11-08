import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { Role } from '../../../enums/role.enum';
import { ROLE_LABELS } from '../../../core/auth/roles';
import { SessionStore, initialsOf } from '../../../core/stores/session.store';
import { relativeTime } from '../../../core/time';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { DIALOG_DEFAULTS } from '../../../shared/ui/dialog';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { AdminTeam, AdminUser } from './admin-users.service';
import { ImportUsersData, ImportUsersDialogComponent } from './import-users-dialog';
import { TeamDialogComponent, TeamFormData, TeamMembersData, TeamMembersDialogComponent } from './team-dialogs';
import { ASSIGNABLE_ROLES, AddUserData, AddUserDialogComponent, ResetPasswordData, ResetPasswordDialogComponent } from './user-dialogs';
import { RoleFilter, TeamCard, UsersAdminStore } from './users-admin.store';

type Tab = 'users' | 'teams';

const ROLE_POWERS: Record<string, string> = {
  [Role.MEMBER]: 'They can play every published lab and use the environments an admin starts for them. Nothing else.',
  [Role.FACILITATOR]: 'They can start and stop the environments of the labs they are assigned to, run the live event and award points.',
  [Role.CREATOR]: 'They can build and edit labs and environments. They do not manage users or teams.',
  [Role.ADMIN]: 'They can manage every user, team, lab, environment and server.',
};

@Component({
  selector: 'app-admin-users',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, EmptyStateComponent, SkeletonComponent],
  providers: [UsersAdminStore],
  templateUrl: './admin-users.page.html',
  styles: `
    .sel-sm { min-height: var(--seg-h); font-size: 13px; padding: 0 8px; width: auto; }
    .bulk { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 10px 16px; background: var(--accent-soft); border-bottom: 1px solid var(--border); }
    .filters .search { margin: 0; max-width: 320px; }
    .pick-box { width: 16px; height: 16px; accent-color: var(--accent); }
    .actions { flex-wrap: nowrap; }
    .members .avatar { margin-right: -6px; border: 2px solid var(--surface); }
    .team-card .desc { margin: 0; }
    .team-note { margin: 0 0 12px; max-width: 760px; }
  `,
  host: { class: 'content' },
})
export class AdminUsersPage {
  protected readonly store = inject(UsersAdminStore);
  protected readonly session = inject(SessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  /** From the ?tab= query parameter. */
  readonly tabParam = input<string>(undefined, { alias: 'tab' });
  protected readonly tab = linkedSignal<Tab>(() => (this.tabParam() === 'teams' ? 'teams' : 'users'));
  readonly queryParam = input<string>(undefined, { alias: 'q' });

  protected readonly roles = ASSIGNABLE_ROLES;
  protected readonly labels = ROLE_LABELS;
  protected readonly roleFilters: { value: RoleFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    ...ASSIGNABLE_ROLES.map(role => ({ value: role as RoleFilter, label: ROLE_LABELS[role] })),
  ];
  protected readonly initials = initialsOf;
  protected readonly moving = signal(false);
  protected readonly lastSeen = relativeTime;

  protected readonly subtitle = computed(() => {
    const users = this.store.users().length;
    const teams = this.store.teams().length;
    return `${users} ${users === 1 ? 'account' : 'accounts'} · ${this.store.disabledCount()} disabled · ${teams} ${teams === 1 ? 'team' : 'teams'}`;
  });

  constructor() {
    this.store.load();
    // ?q= from the top bar search
    effect(() => {
      const query = this.queryParam();
      if (query !== undefined) untracked(() => this.store.setQuery(query));
    });
  }

  protected isMe(user: AdminUser): boolean {
    return user.id === this.session.userId();
  }

  protected showTab(tab: Tab): void {
    this.tab.set(tab);
    this.router.navigate([], { queryParams: { tab: tab === 'teams' ? 'teams' : null }, replaceUrl: true });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  // Users

  protected addUser(): void {
    const data: AddUserData = {
      teams: this.store.teams(),
      save: async user => {
        const created = await this.store.createUser(user);
        this.toast.success(`${created.username} can sign in now`);
      },
    };
    this.dialog.open(AddUserDialogComponent, { ...DIALOG_DEFAULTS, width: '520px', data });
  }

  protected async changeRole(user: AdminUser, event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const role = select.value as Role;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Make ${user.username} ${article(ROLE_LABELS[role])} ${ROLE_LABELS[role]}?`,
      message: `${ROLE_POWERS[role]} The change applies on their next request; they stay signed in.`,
      confirmLabel: `Make ${ROLE_LABELS[role]}`,
      tone: 'primary',
      icon: 'shield',
    }));
    if (answer !== 'confirm') {
      select.value = user.role;
      return;
    }
    try {
      await this.store.changeRole(user.id, role);
      this.toast.success(`${user.username} is now ${article(ROLE_LABELS[role])} ${ROLE_LABELS[role]}`);
    } catch (error) {
      select.value = user.role;
      this.toast.error(`The role of ${user.username} could not be changed.`, error);
    }
  }

  protected async changeTeam(user: AdminUser, event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const teamId = select.value || null;
    try {
      const updated = await this.store.moveToTeam(user.id, teamId);
      this.toast.success(updated.team ? `${user.username} moved to ${updated.team.name}` : `${user.username} has no team now`);
    } catch (error) {
      select.value = user.team?.id ?? '';
      this.toast.error(`${user.username} could not be moved.`, error);
    }
  }

  protected resetPassword(user: AdminUser): void {
    const data: ResetPasswordData = {
      username: user.username,
      save: async password => {
        await this.store.resetPassword(user.id, password);
        this.toast.success(`Password reset. ${user.username} was signed out everywhere.`);
      },
    };
    this.dialog.open(ResetPasswordDialogComponent, { ...DIALOG_DEFAULTS, data });
  }

  protected async disable(user: AdminUser): Promise<void> {
    const answer = await firstValueFrom(this.confirm.open({
      title: `Disable ${user.username}?`,
      message: 'Their next request is rejected and they are signed out. Their points, solves and team stay as they are, '
        + "and their team's labs keep running. You can enable the account again at any time.",
      confirmLabel: 'Disable account',
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.setDisabled(user.id, true);
      this.toast.success(`${user.username} is disabled`);
    } catch (error) {
      this.toast.error(`${user.username} could not be disabled.`, error);
    }
  }

  protected async enable(user: AdminUser): Promise<void> {
    try {
      await this.store.setDisabled(user.id, false);
      this.toast.success(`${user.username} can sign in again`);
    } catch (error) {
      this.toast.error(`${user.username} could not be enabled.`, error);
    }
  }

  protected async bulkMove(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const choice = select.value;
    select.value = '';
    if (!choice) {
      return;
    }
    const teamId = choice === 'none' ? null : choice;
    const what = teamId ? `moved to ${this.store.teams().find(t => t.id === teamId)?.name}` : 'taken off their team';
    this.moving.set(true);
    try {
      const moved = await this.store.moveSelected(teamId);
      this.toast.success(`${moved.length} ${moved.length === 1 ? 'account' : 'accounts'} ${what}`);
    } catch (error) {
      this.toast.error('The accounts could not be moved. Nobody was moved.', error);
    } finally {
      this.moving.set(false);
    }
  }

  protected importCsv(): void {
    const data: ImportUsersData = {
      teams: this.store.teams(),
      existing: this.store.users().map(user => user.username),
      run: async rows => {
        const result = await this.store.importUsers(rows);
        if (result.created.length) {
          this.toast.success(`${result.created.length} ${result.created.length === 1 ? 'account' : 'accounts'} imported`);
        }
        return result;
      },
    };
    this.dialog.open(ImportUsersDialogComponent, { ...DIALOG_DEFAULTS, width: '880px', data, disableClose: true });
  }

  protected async bulkRole(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const role = select.value as Role;
    select.value = '';
    if (!role) {
      return;
    }
    const me = this.session.userId() ?? '';
    const count = this.store.selected().filter(id => id !== me).length;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Make ${count} ${count === 1 ? 'account' : 'accounts'} ${ROLE_LABELS[role]}?`,
      message: `${ROLE_POWERS[role]} Your own role is left as it is.`,
      confirmLabel: `Make ${ROLE_LABELS[role]}`,
      tone: 'primary',
      icon: 'shield',
    }));
    if (answer !== 'confirm') {
      return;
    }
    const { done, failed } = await this.store.forSelected(id => this.store.changeRole(id, role), [me]);
    this.report(done, failed, `made ${ROLE_LABELS[role]}`);
  }

  protected async bulkDisable(): Promise<void> {
    const me = this.session.userId() ?? '';
    const count = this.store.selected().filter(id => id !== me).length;
    if (!count) {
      this.toast.info('You cannot disable your own account');
      return;
    }
    const answer = await firstValueFrom(this.confirm.open({
      title: `Disable ${count} ${count === 1 ? 'account' : 'accounts'}?`,
      message: 'Each one is signed out and rejected on their next request. Points, solves and teams stay as they are. '
        + 'Your own account is skipped.',
      confirmLabel: 'Disable accounts',
    }));
    if (answer !== 'confirm') {
      return;
    }
    const { done, failed } = await this.store.forSelected(id => this.store.setDisabled(id, true), [me]);
    this.report(done, failed, 'disabled');
  }

  private report(done: number, failed: unknown, what: string): void {
    if (done) {
      this.toast.success(`${done} ${done === 1 ? 'account' : 'accounts'} ${what}`);
    }
    if (failed) {
      this.toast.error('Some accounts could not be changed.', failed);
    }
  }

  // Teams

  protected newTeam(): void {
    const data: TeamFormData = {
      save: async (name, description) => {
        await this.store.createTeam(name, description);
        this.toast.success(`${name} created`);
      },
    };
    this.dialog.open(TeamDialogComponent, { ...DIALOG_DEFAULTS, data });
  }

  protected editTeam(team: AdminTeam): void {
    const data: TeamFormData = {
      team,
      save: async (name, description) => {
        await this.store.editTeam(team.id, name, description);
        this.toast.success(`${name} saved`);
      },
    };
    this.dialog.open(TeamDialogComponent, { ...DIALOG_DEFAULTS, data });
  }

  protected members(team: AdminTeam): void {
    const data: TeamMembersData = {
      team,
      users: this.store.users,
      move: (userId, teamId) => this.store.moveToTeam(userId, teamId),
    };
    this.dialog.open(TeamMembersDialogComponent, { ...DIALOG_DEFAULTS, width: '520px', data });
  }

  protected async deleteTeam(card: TeamCard): Promise<void> {
    const { team, members } = card;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${team.name}?`,
      message: `${members.length ? `Its ${members.length} ${members.length === 1 ? 'member stays' : 'members stay'} as accounts without a team and play solo. ` : ''}`
        + 'Its stopped environment copies are removed with it. Points stay with the players.',
      confirmLabel: 'Delete team',
      typeToConfirm: team.name,
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.deleteTeam(team.id);
      this.toast.success(`${team.name} deleted`);
    } catch (error) {
      this.toast.error(`${team.name} could not be deleted.`, error);
    }
  }

  protected deleteNote(card: TeamCard): string {
    return card.deployed ? 'Stop its environment copies first' : `Delete ${card.team.name}`;
  }
}

function article(word: string): string {
  return /^[AEIOU]/i.test(word) ? 'an' : 'a';
}
