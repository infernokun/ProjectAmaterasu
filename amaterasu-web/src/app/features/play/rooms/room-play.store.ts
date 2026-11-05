import { computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { roomActivity } from '../../../core/room-activity';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { WebsocketService } from '../../../services/websocket.service';
import { PlayApiService } from '../data/play-api.service';
import { ChallengeView, HintView, RoomOverview } from '../data/play.models';

export type StatusFilter = 'all' | 'open' | 'solved';

interface RoomPlayState {
  roomId: string | null;
  overview: RoomOverview | null;
  loading: boolean;
  error: string | null;
  selectedId: string | null;
  category: string;
  status: StatusFilter;
  submitting: boolean;
  /** The last wrong answer's challenge, so its banner shows until the player types again. */
  wrongFor: string | null;
  answerError: string | null;
  hintBusy: string | null;
  joining: boolean;
}

export interface SubmitOutcome {
  correct: boolean;
  points: number;
}

/** One room as a player sees it. Provided by the room page. */
export const RoomPlayStore = signalStore(
  withState<RoomPlayState>({
    roomId: null, overview: null, loading: false, error: null, selectedId: null, category: 'All', status: 'all',
    submitting: false, wrongFor: null, answerError: null, hintBusy: null, joining: false,
  }),
  withComputed(({ overview, selectedId, category, status }) => {
    const challenges = computed(() => overview()?.challenges ?? []);
    const categories = computed(() => ['All', ...new Set(challenges().map(challenge => challenge.category))]);
    const visible = computed(() => challenges().filter(challenge =>
      (category() === 'All' || challenge.category === category())
      && (status() === 'all' || (status() === 'solved' ? challenge.solved : !challenge.solved))));
    const groups = computed(() => categories().slice(1)
      .map(name => {
        const inCategory = challenges().filter(challenge => challenge.category === name);
        return {
          name,
          total: inCategory.length,
          done: inCategory.filter(challenge => challenge.solved).length,
          items: visible().filter(challenge => challenge.category === name),
        };
      })
      .filter(group => group.items.length > 0));
    const selected = computed<ChallengeView | undefined>(() =>
      challenges().find(challenge => challenge.id === selectedId()) ?? visible()[0] ?? challenges()[0]);
    return {
      challenges,
      categories,
      groups,
      selected,
      joined: computed(() => !!overview()?.me.joined),
      solvedCount: computed(() => challenges().filter(challenge => challenge.solved).length),
    };
  }),
  withMethods((store, api = inject(PlayApiService), session = inject(SessionStore), rooms = inject(RoomsStore)) => {
    async function reload(): Promise<void> {
      const roomId = store.roomId();
      if (!roomId) return;
      try {
        const overview = await firstValueFrom(api.overview(roomId));
        if (store.roomId() === roomId) {
          patchState(store, { overview, error: null });
        }
      } catch (error) {
        patchState(store, { error: errorMessage(error) ?? 'This room could not be loaded.' });
      }
    }

    return {
      reload,
      async load(roomId: string): Promise<void> {
        if (store.roomId() === roomId && store.overview()) return;
        patchState(store, { roomId, overview: null, loading: true, error: null, wrongFor: null, answerError: null });
        await reload();
        patchState(store, { loading: false });
        if (store.overview()?.me.joined) {
          rooms.setCurrentRoom(roomId);
        }
      },
      select(id: string): void {
        patchState(store, { selectedId: id, wrongFor: null, answerError: null });
      },
      setCategory(category: string): void {
        patchState(store, { category });
      },
      setStatus(status: StatusFilter): void {
        patchState(store, { status });
      },
      clearAnswerError(): void {
        if (store.wrongFor() || store.answerError()) patchState(store, { wrongFor: null, answerError: null });
      },
      async submit(challenge: ChallengeView, flag: string): Promise<SubmitOutcome | null> {
        const roomId = store.roomId();
        if (!roomId || !flag.trim()) return null;
        patchState(store, { submitting: true, wrongFor: null, answerError: null });
        try {
          const result = await firstValueFrom(api.submitFlag(roomId, challenge.id, flag.trim()));
          if (!result.correct) patchState(store, { wrongFor: challenge.id });
          await reload();
          return { correct: !!result.correct, points: challenge.points };
        } catch (error) {
          patchState(store, { answerError: errorMessage(error) ?? 'That flag could not be checked. Try again.' });
          return null;
        } finally {
          patchState(store, { submitting: false });
        }
      },
      async unlockHint(challenge: ChallengeView, hint: HintView): Promise<string | null> {
        const roomId = store.roomId();
        const userId = session.userId();
        if (!roomId || !userId) return 'Sign in again to unlock hints.';
        patchState(store, { hintBusy: hint.id });
        try {
          await firstValueFrom(api.unlockHint(roomId, userId, challenge.id, hint.id));
          await reload();
          return null;
        } catch (error) {
          return errorMessage(error) ?? 'The hint could not be unlocked.';
        } finally {
          patchState(store, { hintBusy: null });
        }
      },
      async join(): Promise<string | null> {
        const roomId = store.roomId();
        if (!roomId) return null;
        patchState(store, { joining: true });
        try {
          await firstValueFrom(rooms.join(roomId));
          rooms.setCurrentRoom(roomId);
          await reload();
          return null;
        } catch (error) {
          return errorMessage(error) ?? 'Could not join this room.';
        } finally {
          patchState(store, { joining: false });
        }
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
