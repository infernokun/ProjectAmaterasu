import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, firstValueFrom, forkJoin, pipe, tap } from 'rxjs';
import { Role } from '../../../enums/role.enum';
import { errorMessage } from '../../../core/http-error';
import { isDeployed, isRunning } from '../../../core/lab-deployment';
import { RequestState, initialRequestState } from '../../../core/stores/request-status';
import { AdminTeam, AdminUser, AdminUsersService, NewUser, TeamLab } from './admin-users.service';

export type RoleFilter = Role | 'ALL';

interface UsersAdminState extends RequestState {
  users: AdminUser[];
  teams: AdminTeam[];
  labs: TeamLab[];
  query: string;
  roleFilter: RoleFilter;
  disabledOnly: boolean;
  selected: string[];
}

export interface TeamCard {
  team: AdminTeam;
  members: AdminUser[];
  running: number;
  deployed: number;
}

const byName = (a: { username: string }, b: { username: string }) => a.username.localeCompare(b.username);

/** Users and teams for the admin screen. */
export const UsersAdminStore = signalStore(
  withState<UsersAdminState>({
    users: [], teams: [], labs: [], query: '', roleFilter: 'ALL', disabledOnly: false, selected: [], ...initialRequestState,
  }),
  withComputed(({ users, teams, labs, query, roleFilter, disabledOnly, selected, status }) => ({
    loading: computed(() => status() === 'idle' || status() === 'loading'),
    disabledCount: computed(() => users().filter(user => user.disabled).length),
    filtered: computed(() => {
      const q = query().trim().toLowerCase();
      return users().filter(user =>
        (roleFilter() === 'ALL' || user.role === roleFilter())
        && (!disabledOnly() || user.disabled)
        && (!q || `${user.username} ${user.team?.name ?? ''}`.toLowerCase().includes(q)));
    }),
    selectedSet: computed(() => new Set(selected())),
    teamCards: computed<TeamCard[]>(() => teams()
      .map(team => {
        const teamLabs = labs().filter(lab => lab.teamId === team.id);
        return {
          team,
          members: users().filter(user => user.team?.id === team.id),
          running: teamLabs.filter(lab => isRunning(lab.labStatus)).length,
          deployed: teamLabs.filter(lab => isDeployed(lab.labStatus)).length,
        };
      })
      .sort((a, b) => a.team.name.localeCompare(b.team.name))),
  })),
  withMethods((store, api = inject(AdminUsersService)) => {
    const replaceUser = (user: AdminUser) =>
      patchState(store, { users: store.users().map(existing => (existing.id === user.id ? user : existing)) });

    return {
      load: rxMethod<void>(pipe(
        tap(() => patchState(store, { status: 'loading', error: null })),
        exhaustMap(() => forkJoin({ users: api.users(), teams: api.teams(), labs: api.teamLabs() }).pipe(
          tap(({ users, teams, labs }) => patchState(store, { users: [...users].sort(byName), teams, labs, status: 'loaded' })),
          catchError(error => {
            patchState(store, { status: 'error', error: errorMessage(error) ?? 'Users and teams could not be loaded.' });
            return EMPTY;
          }),
        )),
      )),
      setQuery(query: string): void {
        patchState(store, { query });
      },
      setRoleFilter(roleFilter: RoleFilter): void {
        patchState(store, { roleFilter });
      },
      setDisabledOnly(disabledOnly: boolean): void {
        patchState(store, { disabledOnly });
      },
      toggleSelected(userId: string): void {
        const selected = store.selected();
        patchState(store, { selected: selected.includes(userId) ? selected.filter(id => id !== userId) : [...selected, userId] });
      },
      clearSelection(): void {
        patchState(store, { selected: [] });
      },

      async createUser(user: NewUser): Promise<AdminUser> {
        const created = await firstValueFrom(api.createUser(user));
        patchState(store, { users: [...store.users(), created].sort(byName) });
        return created;
      },
      async changeRole(userId: string, role: Role): Promise<AdminUser> {
        const user = await firstValueFrom(api.changeRole(userId, role));
        replaceUser(user);
        return user;
      },
      async moveToTeam(userId: string, teamId: string | null): Promise<AdminUser> {
        const user = await firstValueFrom(api.setTeam(userId, teamId));
        replaceUser(user);
        return user;
      },
      async setDisabled(userId: string, disabled: boolean): Promise<AdminUser> {
        const user = await firstValueFrom(api.setDisabled(userId, disabled));
        replaceUser(user);
        return user;
      },
      async resetPassword(userId: string, password: string): Promise<void> {
        await firstValueFrom(api.resetPassword(userId, password));
      },

      /** Applies an action to each selected user; returns how many succeeded and the first error. */
      async forSelected(action: (userId: string) => Promise<unknown>, skip: string[] = []): Promise<{ done: number; failed: unknown }> {
        let done = 0;
        let failed: unknown = null;
        for (const userId of store.selected().filter(id => !skip.includes(id))) {
          try {
            await action(userId);
            done++;
          } catch (error) {
            failed ??= error;
          }
        }
        patchState(store, { selected: [] });
        return { done, failed };
      },

      async createTeam(name: string, description: string): Promise<AdminTeam> {
        const team = await firstValueFrom(api.createTeam(name, description));
        patchState(store, { teams: [...store.teams(), team] });
        return team;
      },
      async editTeam(teamId: string, name: string, description: string): Promise<AdminTeam> {
        const team = await firstValueFrom(api.editTeam(teamId, name, description));
        patchState(store, {
          teams: store.teams().map(existing => (existing.id === teamId ? team : existing)),
          users: store.users().map(user => (user.team?.id === teamId ? { ...user, team: { ...user.team, name: team.name } } : user)),
        });
        return team;
      },
      async deleteTeam(teamId: string): Promise<void> {
        await firstValueFrom(api.deleteTeam(teamId));
        patchState(store, {
          teams: store.teams().filter(team => team.id !== teamId),
          users: store.users().map(user => (user.team?.id === teamId ? { ...user, team: null } : user)),
          labs: store.labs().filter(lab => lab.teamId !== teamId),
        });
      },
    };
  }),
);
