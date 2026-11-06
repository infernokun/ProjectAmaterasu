import { computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { roomActivity } from '../../../core/room-activity';
import { SessionStore } from '../../../core/stores/session.store';
import { WebsocketService } from '../../../services/websocket.service';
import { PlayApiService } from '../data/play-api.service';
import { RoomStandings } from '../data/play.models';

export type ScoreView = 'players' | 'teams';

interface ScoreboardState {
  roomId: string | null;
  standings: RoomStandings | null;
  loading: boolean;
  error: string | null;
  view: ScoreView;
  query: string;
  updatedAt: number | null;
}

/** A room's standings, kept live. Provided by the scoreboard pages. */
export const ScoreboardStore = signalStore(
  withState<ScoreboardState>({ roomId: null, standings: null, loading: false, error: null, view: 'players', query: '', updatedAt: null }),
  withComputed(({ standings }, session = inject(SessionStore)) => ({
    players: computed(() => standings()?.players ?? []),
    teams: computed(() => standings()?.teams ?? []),
    me: computed(() => standings()?.players.find(player => player.userId === session.userId())),
    myTeam: computed(() => standings()?.teams.find(team => team.teamId === session.teamId())),
  })),
  withMethods((store, api = inject(PlayApiService)) => {
    async function reload(): Promise<void> {
      const roomId = store.roomId();
      if (!roomId) return;
      try {
        const standings = await firstValueFrom(api.standings(roomId));
        if (store.roomId() === roomId) patchState(store, { standings, error: null, updatedAt: Date.now() });
      } catch (error) {
        patchState(store, { error: errorMessage(error) ?? 'The scoreboard could not be loaded.' });
      }
    }
    return {
      reload,
      async load(roomId: string): Promise<void> {
        if (store.roomId() === roomId && store.standings()) return;
        patchState(store, { roomId, standings: null, loading: true, error: null });
        await reload();
        patchState(store, { loading: false });
      },
      setView(view: ScoreView): void {
        patchState(store, { view });
      },
      setQuery(query: string): void {
        patchState(store, { query });
      },
    };
  }),
  withHooks({
    onInit(store, socket = inject(WebsocketService)) {
      roomActivity(socket.getMessage(), roomId => roomId === store.roomId())
        .pipe(takeUntilDestroyed())
        .subscribe(() => store.reload());
    },
  }),
);
