import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { errorMessage } from '../../../core/http-error';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { RequestState, initialRequestState } from '../../../core/stores/request-status';
import { fromLocalInput, toDate, toLocalInput } from '../../../core/time';
import { AuthorChallenge, AuthorFlag, AuthorHint, AuthorRoom, AuthoringService, LabOption, Person } from './authoring.service';
import { exportChallenges, parseImport } from './challenge-io';

/** A challenge being edited: dates as datetime-local values in the browser's time zone. */
export interface Draft extends Omit<AuthorChallenge, 'releaseDate' | 'expirationDate'> {
  opens: string;
  closes: string;
}

interface RoomEditorState extends RequestState {
  room: AuthorRoom | null;
  /** In the order players see them. */
  challenges: AuthorChallenge[];
  facilitators: Person[];
  candidates: Person[];
  labs: LabOption[];
  savingOrder: boolean;
  players: number;
  selectedId: string | null;
  draft: Draft | null;
  dirty: boolean;
  saving: boolean;
}

export const NEW_ID = 'new';

export function toDraft(challenge: AuthorChallenge): Draft {
  const { releaseDate, expirationDate, ...rest } = challenge;
  return {
    ...rest,
    flags: challenge.flags.map(f => ({ ...f })),
    hints: challenge.hints.map(h => ({ ...h })),
    opens: toLocalInput(releaseDate),
    closes: toLocalInput(expirationDate),
  };
}

export function fromDraft(draft: Draft): AuthorChallenge {
  const { opens, closes, ...rest } = draft;
  return {
    ...rest,
    question: draft.question.trim(),
    category: draft.category.trim() || 'General',
    flags: draft.flags.filter(f => f.flag.trim()).map(f => ({ ...f, flag: f.flag.trim() })),
    hints: draft.hints.filter(h => h.hint.trim()).map(h => ({ ...h, hint: h.hint.trim() })),
    releaseDate: fromLocalInput(opens) ?? fromLocalInput(toLocalInput(new Date()))!,
    expirationDate: fromLocalInput(closes),
  };
}

/** Why a draft cannot be saved yet, or null. */
export function draftProblem(draft: Draft): string | null {
  if (!draft.question.trim()) {
    return 'Give the challenge a title.';
  }
  if (!draft.flags.some(f => f.flag.trim())) {
    return 'Add at least one flag players can submit.';
  }
  if (!(draft.points >= 0)) {
    return 'Points cannot be negative.';
  }
  if (!(draft.maxAttempts >= 1)) {
    return 'Allow at least one attempt.';
  }
  if (draft.opens && draft.closes && new Date(draft.closes) <= new Date(draft.opens)) {
    return 'It has to close after it opens.';
  }
  return null;
}

/** One room's settings and challenges, with the challenge being edited. */
export const RoomEditorStore = signalStore(
  withState<RoomEditorState>({
    room: null, challenges: [], facilitators: [], candidates: [], labs: [], savingOrder: false,
    players: 0, selectedId: null, draft: null, dirty: false, saving: false, ...initialRequestState,
  }),
  withComputed(({ challenges, draft, facilitators, candidates, labs }) => ({
    /** Facilitators who are not on the room yet. */
    addable: computed(() => {
      const taken = new Set(facilitators().map(person => person.id));
      return candidates().filter(person => !taken.has(person.id));
    }),
    labName: computed(() => {
      const names = new Map(labs().map(lab => [lab.id, lab.name]));
      return (labId: string | null) => (labId ? names.get(labId) ?? 'A deleted lab' : null);
    }),
    totalPoints: computed(() => challenges().reduce((sum, c) => sum + (c.points ?? 0), 0)),
    categories: computed(() => [...new Set(challenges().map(c => c.category).filter(Boolean))].sort()),
    problem: computed(() => (draft() ? draftProblem(draft()!) : null)),
    isNew: computed(() => draft()?.id === undefined),
  })),
  withMethods((store, api = inject(AuthoringService), rooms = inject(RoomsStore)) => {

    const select = (challenge: AuthorChallenge | null) => patchState(store, {
      selectedId: challenge?.id ?? null, draft: challenge ? toDraft(challenge) : null, dirty: false,
    });

    const reload = async (roomId: string, keepId?: string | null) => {
      const challenges = await firstValueFrom(api.challenges(roomId));
      patchState(store, { challenges });
      const keep = challenges.find(c => c.id === keepId) ?? challenges[0] ?? null;
      select(keep);
    };

    return {
      async init(roomId: string): Promise<void> {
        patchState(store, { status: 'loading', error: null, room: null, challenges: [], draft: null, selectedId: null });
        try {
          const room = await firstValueFrom(api.room(roomId));
          patchState(store, { room });
          await reload(roomId);
          patchState(store, { status: 'loaded' });
          firstValueFrom(api.players(roomId)).then(players => patchState(store, { players }), () => undefined);
          firstValueFrom(api.facilitators(roomId)).then(facilitators => patchState(store, { facilitators }), () => undefined);
          firstValueFrom(api.facilitatorCandidates()).then(candidates => patchState(store, { candidates }), () => undefined);
          firstValueFrom(api.labs()).then(labs => patchState(store, { labs }), () => undefined);
        } catch (error) {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'The room could not be loaded.' });
        }
      },
      select(challengeId: string): void {
        select(store.challenges().find(c => c.id === challengeId) ?? null);
      },
      newChallenge(author: string | null): void {
        patchState(store, {
          selectedId: NEW_ID,
          dirty: true,
          draft: {
            roomId: store.room()?.id ?? null, labId: null, question: '', category: store.categories()[0] ?? '', difficultyLevel: DifficultyLevel.EASY,
            points: 100, maxAttempts: 10, description: '', visible: true, solutionExplanation: '', author,
            flags: [{ flag: '', caseSensitive: true, surroundWithTag: true, weight: 1 }], hints: [],
            solveCount: 0, attemptCount: 0, opens: toLocalInput(new Date()), closes: '',
          },
        });
      },
      patch(change: Partial<Draft>): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, ...change }, dirty: true });
        }
      },
      patchFlag(index: number, change: Partial<AuthorFlag>): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, flags: draft.flags.map((f, i) => (i === index ? { ...f, ...change } : f)) }, dirty: true });
        }
      },
      addFlag(): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, flags: [...draft.flags, { flag: '', caseSensitive: true, surroundWithTag: true, weight: 1 }] }, dirty: true });
        }
      },
      removeFlag(index: number): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, flags: draft.flags.filter((_, i) => i !== index) }, dirty: true });
        }
      },
      patchHint(index: number, change: Partial<AuthorHint>): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, hints: draft.hints.map((h, i) => (i === index ? { ...h, ...change } : h)) }, dirty: true });
        }
      },
      addHint(): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, hints: [...draft.hints, { hint: '', cost: 25, unlockAfterAttempts: 0 }] }, dirty: true });
        }
      },
      removeHint(index: number): void {
        const draft = store.draft();
        if (draft) {
          patchState(store, { draft: { ...draft, hints: draft.hints.filter((_, i) => i !== index) }, dirty: true });
        }
      },
      /** Discards edits to the selected challenge, or drops an unsaved new one. */
      revert(): void {
        select(store.challenges().find(c => c.id === store.selectedId()) ?? store.challenges()[0] ?? null);
      },
      async save(): Promise<AuthorChallenge> {
        const draft = store.draft();
        if (!draft || draftProblem(draft)) {
          throw new Error(draftProblem(draft!) ?? 'Nothing to save');
        }
        patchState(store, { saving: true });
        try {
          const challenge = fromDraft(draft);
          const saved = challenge.id ? await firstValueFrom(api.update(challenge)) : await firstValueFrom(api.create(challenge));
          const existing = store.challenges().some(c => c.id === saved.id);
          patchState(store, {
            challenges: existing ? store.challenges().map(c => (c.id === saved.id ? saved : c)) : [...store.challenges(), saved],
          });
          select(saved);
          return saved;
        } finally {
          patchState(store, { saving: false });
        }
      },
      async deleteSelected(): Promise<void> {
        const id = store.draft()?.id;
        if (!id) {
          select(store.challenges()[0] ?? null);
          return;
        }
        await firstValueFrom(api.delete(id));
        const challenges = store.challenges().filter(c => c.id !== id);
        patchState(store, { challenges });
        select(challenges[0] ?? null);
      },
      async saveRoom(name: string, surroundTag: string): Promise<AuthorRoom> {
        const room = await firstValueFrom(api.updateRoom(store.room()!.id, name, surroundTag));
        patchState(store, { room });
        rooms.load();
        return room;
      },
      /** Replaces the room's facilitators with these accounts. */
      async setFacilitators(userIds: string[]): Promise<Person[]> {
        const facilitators = await firstValueFrom(api.setFacilitators(store.room()!.id, userIds));
        patchState(store, { facilitators });
        return facilitators;
      },
      /** Moves a challenge in the list and saves the new order; puts the old order back if saving fails. */
      async move(from: number, to: number): Promise<boolean> {
        const before = store.challenges();
        if (from === to || from < 0 || to < 0 || from >= before.length || to >= before.length) {
          return false;
        }
        const next = [...before];
        const [moved] = next.splice(from, 1);
        next.splice(to, 0, moved);
        patchState(store, { challenges: next, savingOrder: true });
        try {
          await firstValueFrom(api.reorder(store.room()!.id, next.map(c => c.id!)));
          return true;
        } catch (error) {
          patchState(store, { challenges: before });
          throw error;
        } finally {
          patchState(store, { savingOrder: false });
        }
      },
      async deleteRoom(): Promise<void> {
        await firstValueFrom(api.deleteRoom(store.room()!.id));
        rooms.load();
      },
      /** Imports an exported file; returns how many challenges were added. */
      async importFile(text: string): Promise<number> {
        const room = store.room()!;
        const { challenges, error } = parseImport(text, room.id);
        if (error) {
          throw new Error(error);
        }
        const count = await firstValueFrom(api.importChallenges(challenges));
        await reload(room.id, store.selectedId());
        return count;
      },
      exportJson(): string {
        return JSON.stringify(exportChallenges(store.challenges()), null, 2);
      },
    };
  }),
);

/** "14:00" today, or "5 Nov 09:00". */
export function scheduleLabel(releaseDate: string | null): string | null {
  const date = toDate(releaseDate);
  if (!date || date.getTime() <= Date.now()) {
    return null;
  }
  const time = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  return date.toDateString() === new Date().toDateString()
    ? time
    : `${date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })} ${time}`;
}
