import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, pipe, switchMap, tap } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { AuditQuery, AuditService } from '../../../core/services/audit.service';
import { RequestState, initialRequestState } from '../../../core/stores/request-status';
import { AuditEvent } from '../../../models/audit-event.model';
import { AdminTeam, AdminUsersService } from '../users/admin-users.service';
import { ACTION_GROUPS } from './audit-actions';

export type TimeRange = 'hour' | 'day' | 'week' | 'all';

const RANGE_MS: Record<TimeRange, number | null> = { hour: 3_600_000, day: 86_400_000, week: 7 * 86_400_000, all: null };

export const PAGE_SIZE = 50;

interface AuditState extends RequestState {
  events: AuditEvent[];
  total: number;
  page: number;
  group: string;
  targetType: string;
  teamId: string;
  range: TimeRange;
  teams: AdminTeam[];
}

/** The audit log screen: filters, paging and team names. */
export const AuditStore = signalStore(
  withState<AuditState>({
    events: [], total: 0, page: 0, group: 'all', targetType: '', teamId: '', range: 'week', teams: [], ...initialRequestState,
  }),
  withComputed(({ events, total, page, teams, status }) => ({
    loading: computed(() => status() === 'idle' || status() === 'loading'),
    teamNames: computed(() => new Map(teams().map(team => [team.id, team.name]))),
    pageCount: computed(() => Math.max(1, Math.ceil(total() / PAGE_SIZE))),
    rangeLabel: computed(() => {
      if (!total()) {
        return 'No events';
      }
      const first = page() * PAGE_SIZE + 1;
      return `Showing ${first} to ${first + events().length - 1} of ${total()}`;
    }),
  })),
  withMethods((store, audit = inject(AuditService), admin = inject(AdminUsersService)) => {
    const query = (): AuditQuery => {
      const range = RANGE_MS[store.range()];
      const actions = ACTION_GROUPS.find(group => group.key === store.group())?.actions ?? [];
      return {
        action: actions.join(',') || undefined,
        targetType: store.targetType() || undefined,
        teamId: store.teamId() || undefined,
        from: range ? new Date(Date.now() - range).toISOString() : undefined,
        page: store.page(),
        size: PAGE_SIZE,
      };
    };
    const fetch = rxMethod<void>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      switchMap(() => audit.list(query()).pipe(
        tap(page => patchState(store, { events: page.content, total: page.totalElements, status: 'loaded' })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'The audit log could not be loaded.' });
          return EMPTY;
        }),
      )),
    ));
    const loadTeams = rxMethod<void>(pipe(
      switchMap(() => admin.teams().pipe(
        tap(teams => patchState(store, { teams: [...teams].sort((a, b) => a.name.localeCompare(b.name)) })),
        catchError(() => EMPTY),
      )),
    ));
    const refilter = (patch: Partial<AuditState>) => {
      patchState(store, { ...patch, page: 0 });
      fetch();
    };
    return {
      load: () => fetch(),
      loadTeams: () => loadTeams(),
      setGroup: (group: string) => refilter({ group }),
      setTargetType: (targetType: string) => refilter({ targetType }),
      setTeam: (teamId: string) => refilter({ teamId }),
      setRange: (range: TimeRange) => refilter({ range }),
      goTo(page: number): void {
        patchState(store, { page: Math.min(Math.max(0, page), store.pageCount() - 1) });
        fetch();
      },
    };
  }),
  withHooks({
    onInit(store) {
      store.load();
      store.loadTeams();
    },
  }),
);
