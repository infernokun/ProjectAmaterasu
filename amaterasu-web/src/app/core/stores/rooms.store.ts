import { computed, effect, inject, untracked } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, map, of, pipe, switchMap, tap } from 'rxjs';
import { RoomUserStatus } from '../../enums/room-user-status.enum';
import { Room } from '../../models/ctf/room.model';
import { JoinRoomResponse } from '../../models/dto/join-room-response.model';
import { RoomService } from '../../services/ctf/room.service';
import { errorMessage } from '../http-error';
import { readStorage, writeStorage } from '../storage';
import { RequestState, initialRequestState } from './request-status';
import { SessionStore } from './session.store';

interface RoomsState extends RequestState {
  rooms: Room[];
  /** The signed-in user's place in each room, by room id. */
  membership: Record<string, JoinRoomResponse>;
  /** The room the sidebar and Scoreboard point at. */
  currentRoomId: string | null;
}

const currentRoomKey = (userId: string) => `amaterasu.room.${userId}`;

/** Rooms, which of them the user joined, and the current room. */
export const RoomsStore = signalStore(
  { providedIn: 'root' },
  withState<RoomsState>({ rooms: [], membership: {}, currentRoomId: null, ...initialRequestState }),
  withComputed(({ rooms, membership, currentRoomId, status }) => {
    const joinedRooms = computed(() =>
      rooms().filter(room => membership()[room.id!]?.roomUserStatus === RoomUserStatus.JOINED));
    return {
      loading: computed(() => status() === 'loading'),
      joinedRooms,
      byId: computed(() => new Map(rooms().map(room => [room.id!, room]))),
      /** The chosen room if the user is in it, else the first room they joined. */
      currentRoom: computed<Room | undefined>(() => {
        const joined = joinedRooms();
        return joined.find(room => room.id === currentRoomId()) ?? joined[0];
      }),
      pointsIn: computed(() => (roomId: string) => membership()[roomId]?.points ?? 0),
    };
  }),
  withMethods((store, roomService = inject(RoomService), session = inject(SessionStore)) => {
    const setMembership = (response: JoinRoomResponse | undefined) => {
      if (response?.roomId) {
        patchState(store, { membership: { ...store.membership(), [response.roomId]: new JoinRoomResponse(response) } });
      }
    };
    const fetch = rxMethod<string | undefined>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      switchMap(userId => roomService.getAllRooms().pipe(
        map(response => (response.data ?? []).map(room => new Room(room))),
        switchMap(rooms => {
          const ids = rooms.map(room => room.id!).filter(Boolean);
          const membership$: Observable<Record<string, JoinRoomResponse>> = userId && ids.length
            ? roomService.checkJoinable(userId, ids).pipe(map(response => (response.data ?? {}) as unknown as Record<string, JoinRoomResponse>))
            : of({});
          return membership$.pipe(map(membership => ({ rooms, membership })));
        }),
        tap(({ rooms, membership }) => patchState(store, { rooms, membership, status: 'loaded' })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Rooms could not be loaded.' });
          return EMPTY;
        }),
      )),
    ));
    return {
      load(): void {
        fetch(session.userId());
      },
      /** Joins a room; the returned observable emits the new membership. */
      join(roomId: string): Observable<JoinRoomResponse> {
        const userId = session.userId();
        if (!userId) {
          return EMPTY;
        }
        return roomService.joinRoom(roomId, userId).pipe(
          map(response => new JoinRoomResponse(response.data)),
          tap(response => setMembership(response)),
        );
      },
      leave(roomId: string): Observable<JoinRoomResponse> {
        const userId = session.userId();
        if (!userId) {
          return EMPTY;
        }
        return roomService.leaveRoom(roomId, userId).pipe(
          map(response => new JoinRoomResponse(response.data)),
          tap(response => setMembership(response)),
        );
      },
      setMembership,
      setCurrentRoom(roomId: string): void {
        patchState(store, { currentRoomId: roomId });
        const userId = session.userId();
        if (userId) {
          writeStorage(currentRoomKey(userId), roomId);
        }
      },
      reset(): void {
        patchState(store, { rooms: [], membership: {}, currentRoomId: null, ...initialRequestState });
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
