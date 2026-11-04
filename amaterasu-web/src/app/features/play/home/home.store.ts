import { computed, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { catchError, forkJoin, map, of, pipe, switchMap, tap } from 'rxjs';
import { roomActivity } from '../../../core/room-activity';
import { AuditService } from '../../../core/services/audit.service';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { AuditEvent } from '../../../models/audit-event.model';
import { WebsocketService } from '../../../services/websocket.service';
import { PlayApiService } from '../data/play-api.service';
import { RoomOverview, RoomStandings } from '../data/play.models';

interface HomeState {
  /** Overviews of the rooms the user joined, by room id. */
  overviews: Record<string, RoomOverview>;
  standings: RoomStandings | null;
  activity: AuditEvent[];
  activityLoaded: boolean;
}

/** Room progress and team activity for Home. Provided by the page. */
export const HomeStore = signalStore(
  withState<HomeState>({ overviews: {}, standings: null, activity: [], activityLoaded: false }),
  withComputed(({ overviews, standings }, rooms = inject(RoomsStore), session = inject(SessionStore)) => ({
    current: computed(() => {
      const room = rooms.currentRoom();
      return room?.id ? overviews()[room.id] : undefined;
    }),
    me: computed(() => standings()?.players.find(player => player.userId === session.userId())),
    teammates: computed(() => {
      const teamId = session.teamId();
      return teamId ? (standings()?.players ?? []).filter(player => player.teamId === teamId) : [];
    }),
  })),
  withMethods((store, api = inject(PlayApiService), audit = inject(AuditService), rooms = inject(RoomsStore),
               session = inject(SessionStore)) => ({
    loadRooms: rxMethod<void>(pipe(
      switchMap(() => {
        const ids = rooms.joinedRooms().map(room => room.id!).filter(Boolean);
        return ids.length ? forkJoin(ids.map(id => api.overview(id).pipe(catchError(() => of(null))))) : of([]);
      }),
      tap(results => {
        const overviews: Record<string, RoomOverview> = {};
        results.forEach(result => {
          if (result) overviews[result.room.id] = result;
        });
        patchState(store, { overviews });
      }),
    )),
    loadStandings: rxMethod<void>(pipe(
      switchMap(() => {
        const roomId = rooms.currentRoom()?.id;
        return roomId ? api.standings(roomId).pipe(catchError(() => of(null))) : of(null);
      }),
      tap(standings => patchState(store, { standings })),
    )),
    loadActivity: rxMethod<void>(pipe(
      switchMap(() => {
        const teamId = session.teamId();
        return teamId
          ? audit.list({ teamId, size: 8 }).pipe(map(page => page.content ?? []), catchError(() => of([] as AuditEvent[])))
          : of([] as AuditEvent[]);
      }),
      tap(activity => patchState(store, { activity, activityLoaded: true })),
    )),
  })),
  withHooks({
    onInit(store, rooms = inject(RoomsStore), socket = inject(WebsocketService), session = inject(SessionStore)) {
      effect(() => {
        rooms.joinedRooms();
        rooms.currentRoom();
        untracked(() => {
          store.loadRooms();
          store.loadStandings();
        });
      });
      effect(() => {
        session.teamId();
        untracked(() => store.loadActivity());
      });
      roomActivity(socket.getMessage(), roomId => rooms.joinedRooms().some(room => room.id === roomId))
        .pipe(takeUntilDestroyed())
        .subscribe(() => {
          store.loadRooms();
          store.loadStandings();
        });
    },
  }),
);
