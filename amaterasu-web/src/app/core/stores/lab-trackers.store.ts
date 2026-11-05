import { computed, effect, inject, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, filter, firstValueFrom, of, pipe, switchMap, tap } from 'rxjs';
import { LabStatus } from '../../enums/lab-status.enum';
import { LabTracker } from '../../models/lab/lab-tracker.model';
import { ApiResponse } from '../../models/api-response.model';
import { LabActionResult } from '../../models/lab/lab-action-result.model';
import { LabService } from '../../services/lab/lab.service';
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
  /** Catalog labs being started for the team, by lab id. */
  starting: Record<string, true>;
}

/** How a lab action ended, with a message to show. */
export interface LabActionOutcome {
  ok: boolean;
  message: string;
  tracker?: LabTracker;
}

const RUNNING = new Set<LabStatus | undefined>([LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE]);

/**
 * The signed-in user's team labs (lab trackers). Loads when the user or team changes and applies
 * live updates from the socket.
 */
export const LabTrackersStore = signalStore(
  { providedIn: 'root' },
  withState<LabTrackersState>({ trackers: [], pending: {}, updatedAt: null, starting: {}, ...initialRequestState }),
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
      const trackers = store.trackers();
      const index = trackers.findIndex(existing => existing.id === tracker.id);
      const next = index < 0 ? [...trackers, tracker] : trackers.map((existing, i) => (i === index ? tracker : existing));
      patchState(store, { trackers: next, updatedAt: Date.now() });
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
        patchState(store, { trackers: [], pending: {}, updatedAt: null, starting: {}, ...initialRequestState });
      },
    };
  }),
  withMethods((store, labService = inject(LabService), session = inject(SessionStore)) => {
    const request = (tracker: LabTracker, remoteServerId?: string) => ({
      labId: tracker.labStarted?.id,
      labTrackerId: tracker.id,
      teamId: session.teamId(),
      userId: session.userId(),
      remoteServerId: remoteServerId ?? tracker.remoteServer?.id,
    });

    async function run(
      call: Observable<ApiResponse<LabActionResult>>,
      done: string,
      failed: string,
    ): Promise<LabActionOutcome> {
      try {
        const response = await firstValueFrom(call);
        const result = response?.data;
        const tracker = result?.labTracker ? new LabTracker(result.labTracker) : undefined;
        if (tracker?.id && tracker.labStatus === LabStatus.DELETED) {
          store.remove(tracker.id);
        } else if (tracker?.id) {
          store.upsert(tracker);
        }
        return result?.isSuccessful
          ? { ok: true, message: done, tracker }
          : { ok: false, message: result?.output?.trim() || failed, tracker };
      } catch (error) {
        const body = error instanceof HttpErrorResponse ? (error.error as ApiResponse<LabActionResult> | null) : null;
        const tracker = body?.data?.labTracker ? new LabTracker(body.data.labTracker) : undefined;
        if (tracker?.id) {
          store.upsert(tracker);
        }
        return { ok: false, message: body?.data?.output?.trim() || errorMessage(error) || failed, tracker };
      }
    }

    async function withPending(tracker: LabTracker, transition: LabTransition, action: () => Promise<LabActionOutcome>) {
      store.setPending(tracker.id!, transition);
      try {
        return await action();
      } finally {
        store.clearPending(tracker.id!);
      }
    }

    const name = (tracker: LabTracker) => tracker.labStarted?.name ?? 'The lab';

    return {
      /** Starts a catalog lab for the team on a server. */
      async start(labId: string, labName: string, remoteServerId: string): Promise<LabActionOutcome> {
        patchState(store, { starting: { ...store.starting(), [labId]: true } });
        try {
          return await run(
            labService.startLab({ labId, teamId: session.teamId(), userId: session.userId(), remoteServerId }),
            `${labName} is running`,
            `${labName} did not start`,
          );
        } finally {
          const { [labId]: _, ...starting } = store.starting();
          patchState(store, { starting });
        }
      },
      /** Resumes a paused lab where it is, or starts a fresh copy of a stopped one. */
      resume(tracker: LabTracker, remoteServerId?: string): Promise<LabActionOutcome> {
        const fresh = tracker.labStatus !== LabStatus.PAUSED;
        return withPending(tracker, fresh ? 'STARTING' : 'RESUMING', () => run(
          labService.startLab(request(tracker, remoteServerId)),
          fresh ? `${name(tracker)} is running, on a fresh copy` : `${name(tracker)} resumed`,
          fresh ? `${name(tracker)} did not start` : `${name(tracker)} did not resume`,
        ));
      },
      pause(tracker: LabTracker): Promise<LabActionOutcome> {
        return withPending(tracker, 'PAUSING', () => run(
          labService.pauseLab(request(tracker)), `${name(tracker)} paused. Its progress is kept.`, `${name(tracker)} did not pause`));
      },
      stop(tracker: LabTracker): Promise<LabActionOutcome> {
        return withPending(tracker, 'STOPPING', () => run(
          labService.stopLab(request(tracker)), `${name(tracker)} stopped. This copy was destroyed.`, `${name(tracker)} did not stop`));
      },
      /** Tears the lab down if needed and takes it off the team. */
      removeFromTeam(tracker: LabTracker): Promise<LabActionOutcome> {
        const team = session.team()?.name ?? 'your team';
        return withPending(tracker, 'REMOVING', () => run(
          labService.deleteLab(request(tracker)), `${name(tracker)} removed from ${team}`, `${name(tracker)} could not be removed`));
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
