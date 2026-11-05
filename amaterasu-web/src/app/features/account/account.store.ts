import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, firstValueFrom, pipe, switchMap, tap } from 'rxjs';
import { errorMessage } from '../../core/http-error';
import { RequestState, initialRequestState } from '../../core/stores/request-status';
import { AccountService, AccountSession } from './account.service';

interface AccountState extends RequestState {
  sessions: AccountSession[];
  changing: boolean;
  passwordChanged: boolean;
  passwordError: string;
  ending: string | null;
}

/** The Account screen's sessions and password change. */
export const AccountStore = signalStore(
  withState<AccountState>({
    sessions: [], changing: false, passwordChanged: false, passwordError: '', ending: null, ...initialRequestState,
  }),
  withComputed(({ sessions, status }) => ({
    loading: computed(() => status() === 'idle' || status() === 'loading'),
    current: computed(() => sessions().find(session => session.current) ?? null),
    others: computed(() => sessions().filter(session => !session.current)),
  })),
  withMethods((store, api = inject(AccountService)) => {
    const loadSessions = rxMethod<void>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      switchMap(() => api.sessions().pipe(
        tap(sessions => patchState(store, { sessions, status: 'loaded' })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Sessions could not be loaded.' });
          return EMPTY;
        }),
      )),
    ));
    return {
      loadSessions: () => loadSessions(),
      clearPasswordNotice(): void {
        if (store.passwordChanged() || store.passwordError()) {
          patchState(store, { passwordChanged: false, passwordError: '' });
        }
      },
      async changePassword(current: string, next: string): Promise<boolean> {
        patchState(store, { changing: true, passwordChanged: false, passwordError: '' });
        try {
          await firstValueFrom(api.changePassword(current, next));
          // The change starts a new session for this browser and ends the rest
          patchState(store, { changing: false, passwordChanged: true });
          loadSessions();
          return true;
        } catch (error) {
          patchState(store, { changing: false, passwordError: errorMessage(error) ?? 'Your password could not be changed.' });
          return false;
        }
      },
      async signOut(sessionId: string): Promise<void> {
        patchState(store, { ending: sessionId });
        try {
          await firstValueFrom(api.signOut(sessionId));
          patchState(store, { sessions: store.sessions().filter(session => session.id !== sessionId) });
        } finally {
          patchState(store, { ending: null });
        }
      },
      async signOutOthers(): Promise<number> {
        const count = store.others().length;
        patchState(store, { ending: 'others' });
        try {
          await firstValueFrom(api.signOutOthers());
          patchState(store, { sessions: store.sessions().filter(session => session.current) });
          return count;
        } finally {
          patchState(store, { ending: null });
        }
      },
    };
  }),
);
