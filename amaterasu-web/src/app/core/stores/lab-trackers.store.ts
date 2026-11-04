import { computed, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, filter, of, pipe, switchMap, tap } from 'rxjs';
import { LabStatus } from '../../enums/lab-status.enum';
import { LabTracker } from '../../models/lab/lab-tracker.model';
import { LabTrackerService } from '../../services/lab/lab-tracker.service';
import { WebsocketService } from '../../services/websocket.service';
import { LabDisplayState, LabTransition } from '../../shared/lab-status';
import { errorMessage } from '../http-error';
import { RequestState, initialRequestState } from './request-status';
import { SessionStore } from './session.store';

/** What the API pushes over the socket when a tracker changes. */
export interface LabTrackerSocketMessage {
  type: 'labTracker';
  action: 'updated' | 'removed';
  labTracker: unknown;
}

export function isLabTrackerMessage(message: unknown): message is LabTrackerSocketMessage {
  return !!message && typeof message === 'object' && (message as { type?: unknown }).type === 'labTracker';
}

interface LabTrackersState extends RequestState {
  trackers: LabTracker[];
  /** Requests in flight, by tracker id. Shown instead of the stored status until they finish. */
  pending: Record<string, LabTransition>;
  /** When the list last changed from the API or the socket (epoch ms). */
  updatedAt: number | null;
}

const RUNNING = new Set<LabStatus | undefined>([LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE]);

/**
 * The signed-in user's team labs (lab trackers). Loads when the user or team changes and applies
 * live updates from the socket.
 */
export const LabTrackersStore = signalStore(
  { providedIn: 'root' },
  withState<LabTrackersState>({ trackers: [], pending: {}, updatedAt: null, ...initialRequestState }),
  withComputed(({ trackers, pending, status }) => ({
    loading: computed(() => status() === 'loading'),
    /** Trackers still on the team; removed ones are left out. */
    teamLabs: computed(() => trackers().filter(tracker => tracker.labStatus !== LabStatus.DELETED)),
    runningCount: computed(() => trackers().filter(tracker => RUNNING.has(tracker.labStatus)).length),
    byId: computed(() => new Map(trackers().map(tracker => [tracker.id!, tracker]))),
    /** The state to show for a tracker: a request in flight, else its stored status. */
    displayState: computed(() => (tracker: LabTracker): LabDisplayState =>
      pending()[tracker.id!] ?? tracker.labStatus ?? LabStatus.NONE),
  })),
  withMethods((store, trackerService = inject(LabTrackerService), session = inject(SessionStore)) => {
    const upsert = (tracker: LabTracker) => {
      const others = store.trackers().filter(existing => existing.id !== tracker.id);
      patchState(store, { trackers: [...others, tracker], updatedAt: Date.now() });
    };
    const remove = (trackerId: string) => {
      const { [trackerId]: _, ...pending } = store.pending();
      patchState(store, { trackers: store.trackers().filter(tracker => tracker.id !== trackerId), pending, updatedAt: Date.now() });
    };
    const fetch = rxMethod<string | undefined>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      switchMap(teamId => (teamId ? trackerService.getLabTrackersByTeam(teamId) : of([])).pipe(
        tap(trackers => patchState(store, { trackers, status: 'loaded', updatedAt: Date.now() })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Team labs could not be loaded.' });
          return EMPTY;
        }),
      )),
    ));
    return {
      load(): void {
        fetch(session.teamId());
      },
      upsert,
      remove,
      setPending(trackerId: string, transition: LabTransition): void {
        patchState(store, { pending: { ...store.pending(), [trackerId]: transition } });
      },
      clearPending(trackerId: string): void {
        const { [trackerId]: _, ...pending } = store.pending();
        patchState(store, { pending });
      },
      /** Applies a socket message; ignores trackers that belong to another team. */
      applySocketMessage(message: LabTrackerSocketMessage): void {
        const tracker = new LabTracker(message.labTracker);
        if (!tracker.id) {
          return;
        }
        const known = store.trackers().some(existing => existing.id === tracker.id);
        const teamId = session.teamId();
        if (!known && (!teamId || tracker.labOwner?.id !== teamId)) {
          return;
        }
        if (message.action === 'removed' || tracker.labStatus === LabStatus.DELETED) {
          remove(tracker.id);
        } else {
          upsert(tracker);
        }
      },
      reset(): void {
        patchState(store, { trackers: [], pending: {}, updatedAt: null, ...initialRequestState });
      },
    };
  }),
  withHooks({
    onInit(store, session = inject(SessionStore), socket = inject(WebsocketService)) {
      effect(() => {
        const signedIn = session.signedIn();
        session.teamId();
        untracked(() => (signedIn ? store.load() : store.reset()));
      });
      socket.getMessage()
        .pipe(filter(isLabTrackerMessage), takeUntilDestroyed())
        .subscribe(message => store.applySocketMessage(message));
    },
  }),
);
