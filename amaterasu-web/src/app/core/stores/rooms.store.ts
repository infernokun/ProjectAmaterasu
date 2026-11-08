import { computed, effect, inject, untracked } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, forkJoin, map, of, pipe, switchMap, tap } from 'rxjs';
import { Room } from '../../models/ctf/room.model';
import { RoomService } from '../../services/ctf/room.service';
import { errorMessage } from '../http-error';
import { readStorage, writeStorage } from '../storage';
import { RequestState, initialRequestState } from './request-status';
import { SessionStore } from './session.store';

export interface RoomProgress {
  points: number;
  solved: number;
}

interface RoomsState extends RequestState {
  rooms: Room[];
  /** The signed-in user's points and solves in each lab, by lab id. */
  progress: Record<string, RoomProgress>;
  /** The room the sidebar and Scoreboard point at. */
  currentRoomId: string | null;
}

const currentRoomKey = (userId: string) => `amaterasu.room.${userId}`;

/** Labs (rooms), the ones the user has played, and the current one. Every signed-in user can play every lab. */
export const RoomsStore = signalStore(
  { providedIn: 'root' },
  withState<RoomsState>({ rooms: [], progress: {}, currentRoomId: null, ...initialRequestState }),
  withComputed(({ rooms, progress, currentRoomId, status }) => {
    const playedRooms = computed(() => rooms().filter(room => {
      const mine = progress()[room.id!];
      return !!mine && (mine.points !== 0 || mine.solved > 0);
    }));
    return {
      loading: computed(() => status() === 'loading'),
      playedRooms,
      byId: computed(() => new Map(rooms().map(room => [room.id!, room]))),
      /** The chosen lab, else the first one the user scored in. */
      currentRoom: computed<Room | undefined>(() =>
        rooms().find(room => room.id === currentRoomId()) ?? playedRooms()[0]),
      pointsIn: computed(() => (roomId: string) => progress()[roomId]?.points ?? 0),
    };
  }),
  withMethods((store, roomService = inject(RoomService), session = inject(SessionStore)) => {
    const fetch = rxMethod<void>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      switchMap(() => forkJoin({
        rooms: roomService.getAllRooms().pipe(map(response => (response.data ?? []).map(room => new Room(room)))),
        progress: roomService.getProgress().pipe(
          map(response => Object.fromEntries((response.data ?? []).map(card => [card.id, { points: card.points ?? 0, solved: card.solved ?? 0 }]))),
          catchError(() => of({} as Record<string, RoomProgress>)),
        ),
      }).pipe(
        tap(({ rooms, progress }) => patchState(store, { rooms, progress, status: 'loaded' })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Labs could not be loaded.' });
          return EMPTY;
        }),
      )),
    ));
    return {
      load(): void {
        fetch();
      },
      setCurrentRoom(roomId: string): void {
        patchState(store, { currentRoomId: roomId });
        const userId = session.userId();
        if (userId) {
          writeStorage(currentRoomKey(userId), roomId);
        }
      },
      reset(): void {
        patchState(store, { rooms: [], progress: {}, currentRoomId: null, ...initialRequestState });
      },
    };
  }),
  withHooks({
    onInit(store, session = inject(SessionStore)) {
      effect(() => {
        const userId = session.userId();
        untracked(() => {
          if (userId) {
            patchState(store, { currentRoomId: readStorage(currentRoomKey(userId)) });
            store.load();
          } else {
            store.reset();
          }
        });
      });
    },
  }),
);
