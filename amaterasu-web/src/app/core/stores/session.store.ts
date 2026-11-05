import { computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { Role } from '../../enums/role.enum';
import { User } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { AREA_ROLES, ROLE_LABELS, rolesFromToken } from '../auth/roles';

interface SessionState {
  user: User | undefined;
  /** From the access token, which is what the API checks. */
  roles: Role[];
}

/** The signed-in user and their roles, kept in step with AuthService. */
export const SessionStore = signalStore(
  { providedIn: 'root' },
  withState<SessionState>({ user: undefined, roles: [] }),
  withComputed(({ user, roles }) => {
    const has = (allowed: readonly Role[]) => roles().some(role => allowed.includes(role));
    return {
      signedIn: computed(() => !!user()),
      userId: computed(() => user()?.id),
      username: computed(() => user()?.username ?? ''),
      team: computed(() => user()?.team),
      teamId: computed(() => user()?.team?.id),
      /** The first role, for display. */
      role: computed<Role | undefined>(() => roles()[0] ?? user()?.role),
      roleLabel: computed(() => {
        const role = roles()[0] ?? user()?.role;
        return role ? ROLE_LABELS[role] : '';
      }),
      initials: computed(() => initialsOf(user()?.username)),
      isAdmin: computed(() => has(AREA_ROLES.admin)),
      canRun: computed(() => has(AREA_ROLES.run)),
      canBuild: computed(() => has(AREA_ROLES.build)),
    };
  }),
  withMethods((store, auth = inject(AuthService)) => ({
    hasRole(...allowed: Role[]): boolean {
      return store.roles().some(role => allowed.includes(role));
    },
    signOut(): void {
      auth.logout();
    },
    /** Replaces the signed-in user's details after the account was edited. */
    updateUser(user: User): void {
      auth.setUser(user);
    },
  })),
  withHooks({
    onInit(store, auth = inject(AuthService)) {
      auth.payload$.pipe(takeUntilDestroyed()).subscribe(payload => {
        patchState(store, {
          user: payload?.user,
          roles: payload ? rolesFromToken(auth.decodeToken(payload.accessToken)) : [],
        });
      });
    },
  }),
);

export function initialsOf(name: string | undefined): string {
  if (!name) {
    return '';
  }
  const parts = name.split(/[\s._-]+/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : name.slice(0, 2);
  return letters.toUpperCase();
}
