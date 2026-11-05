import { computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { filter, firstValueFrom, forkJoin } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { roomActivity } from '../../../core/room-activity';
import { isLabTrackerMessage } from '../../../core/stores/lab-trackers.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { Team } from '../../../models/team.model';
import { WebsocketService } from '../../../services/websocket.service';
import { labStateMeta } from '../../../shared/lab-status';
import { PlayApiService } from '../../play/data/play-api.service';
import { FeedKind, RoomLive } from '../../play/data/play.models';

export type FeedFilter = 'all' | FeedKind;

interface LiveState {
  roomId: string | null;
  live: RoomLive | null;
  trackers: LabTracker[];
  teams: Team[];
  loading: boolean;
  error: string | null;
  dismissed: string[];
  feedFilter: FeedFilter;
  updatedAt: number | null;
}

export interface Alert {
  id: string;
  severity: 'bad' | 'warn';
  title: string;
  body: string;
  at: Date | undefined;
  trackerId?: string;
}

const RUNNING = new Set([LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE]);

/** What a facilitator watches during an event. Provided by the live page. */
export const LiveStore = signalStore(
  withState<LiveState>({
    roomId: null, live: null, trackers: [], teams: [], loading: false, error: null, dismissed: [], feedFilter: 'all', updatedAt: null,
  }),
  withComputed(({ live, trackers, teams, dismissed, feedFilter }) => ({
    labsRunning: computed(() => trackers().filter(tracker => RUNNING.has(tracker.labStatus!)).length),
    degraded: computed(() => trackers().filter(tracker => tracker.labStatus === LabStatus.DEGRADED || tracker.labStatus === LabStatus.ACTIVE_FAILED).length),
    failed: computed(() => trackers().filter(tracker => tracker.labStatus === LabStatus.FAILED).length),
    feed: computed(() => {
      const kind = feedFilter();
      const items = live()?.feed ?? [];
      return kind === 'all' ? items : items.filter(item => item.kind === kind);
    }),
    alerts: computed<Alert[]>(() => {
      const alerts: Alert[] = [];
      const hidden = new Set(dismissed());
      for (const tracker of trackers()) {
        const status = tracker.labStatus;
        if (status !== LabStatus.FAILED && status !== LabStatus.DEGRADED && status !== LabStatus.ACTIVE_FAILED) continue;
        const lab = tracker.labStarted?.name ?? 'A lab';
        const team = tracker.labOwner?.name ?? 'a team';
        const down = (tracker.services ?? []).filter(service => service.state !== 'running').map(service => service.service ?? service.name);
        alerts.push({
          id: `${tracker.id}:${status}:${tracker.updatedAt?.getTime() ?? ''}`,
          severity: status === LabStatus.FAILED ? 'bad' : 'warn',
          title: `${lab} is ${labStateMeta(status).label.toLowerCase()} for ${team}`,
          body: status === LabStatus.FAILED
            ? `The last action failed on ${tracker.remoteServer?.name ?? 'its server'}. The team can start a fresh copy.`
            : down.length ? `${down.join(', ')} ${down.length === 1 ? 'is' : 'are'} not running.` : 'Part of the lab is not running.',
          at: tracker.updatedAt,
          trackerId: tracker.id,
        });
      }
      const servers = new Map<string, { name: string; count: number }>();
      for (const tracker of trackers()) {
        const server = tracker.remoteServer;
        if (server?.id && server.remoteServerStats?.status === LabStatus.OFFLINE) {
          const entry = servers.get(server.id) ?? { name: server.name ?? 'A server', count: 0 };
          entry.count++;
          servers.set(server.id, entry);
        }
      }
      servers.forEach((server, id) => alerts.push({
        id: `server:${id}`,
        severity: 'bad',
        title: `${server.name} is offline`,
        body: `${server.count} team ${server.count === 1 ? 'lab runs' : 'labs run'} on it.`,
        at: undefined,
      }));
      return alerts.filter(alert => !hidden.has(alert.id))
        .sort((a, b) => (a.severity === b.severity ? 0 : a.severity === 'bad' ? -1 : 1));
    }),
    matrix: computed(() => {
      const labs = new Map<string, string>();
      trackers().forEach(tracker => {
        if (tracker.labStarted?.id) labs.set(tracker.labStarted.id, tracker.labStarted.name ?? 'Lab');
      });
      const columns = [...labs.entries()].sort((a, b) => a[1].localeCompare(b[1])).map(([id, name]) => ({ id, name }));
      const byKey = new Map(trackers().map(tracker => [`${tracker.labOwner?.id}:${tracker.labStarted?.id}`, tracker]));
      const teamIds = new Set(trackers().map(tracker => tracker.labOwner?.id));
      const rows = teams()
        .filter(team => teamIds.has(team.id))
        .sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
        .map(team => ({
          team: team.name ?? 'Team',
          cells: columns.map(column => {
            const tracker = byKey.get(`${team.id}:${column.id}`);
            if (!tracker?.labStatus) return { label: 'None', tone: '' };
            const meta = labStateMeta(tracker.labStatus);
            return { label: meta.label, tone: `s-${meta.tone}` };
          }),
        }));
      return { columns, rows };
    }),
  })),
  withMethods((store, api = inject(PlayApiService)) => {
    async function reloadLive(): Promise<void> {
      const roomId = store.roomId();
      if (!roomId) return;
      try {
        const live = await firstValueFrom(api.live(roomId));
        if (store.roomId() === roomId) patchState(store, { live, error: null, updatedAt: Date.now() });
      } catch (error) {
        patchState(store, { error: errorMessage(error) ?? 'The live view could not be loaded.' });
      }
    }
    return {
      reloadLive,
      async load(roomId: string): Promise<void> {
        patchState(store, { roomId, live: null, loading: true, error: null });
        try {
          const [trackers, teams] = await firstValueFrom(forkJoin([api.allTrackers(), api.teams()]));
          patchState(store, { trackers, teams });
        } catch (error) {
          patchState(store, { error: errorMessage(error) ?? 'Team labs could not be loaded.' });
        }
        await reloadLive();
        patchState(store, { loading: false });
      },
      applyTracker(action: 'updated' | 'removed', raw: unknown): void {
        const tracker = new LabTracker(raw);
        if (!tracker.id) return;
        const others = store.trackers().filter(existing => existing.id !== tracker.id);
        const gone = action === 'removed' || tracker.labStatus === LabStatus.DELETED;
        const index = store.trackers().findIndex(existing => existing.id === tracker.id);
        const trackers = gone ? others : index < 0 ? [...others, tracker] : store.trackers().map(existing => (existing.id === tracker.id ? tracker : existing));
        patchState(store, { trackers, updatedAt: Date.now() });
      },
      dismiss(id: string): void {
        patchState(store, { dismissed: [...store.dismissed(), id] });
      },
      setFeedFilter(feedFilter: FeedFilter): void {
        patchState(store, { feedFilter });
      },
    };
  }),
  withHooks({
    onInit(store, socket = inject(WebsocketService)) {
      socket.getMessage().pipe(filter(isLabTrackerMessage), takeUntilDestroyed())
        .subscribe(message => store.applyTracker(message.action, message.labTracker));
      roomActivity(socket.getMessage(), roomId => roomId === store.roomId())
        .pipe(takeUntilDestroyed())
        .subscribe(() => store.reloadLive());
    },
  }),
);
