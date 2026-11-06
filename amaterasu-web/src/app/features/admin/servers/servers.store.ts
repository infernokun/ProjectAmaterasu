import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, firstValueFrom, forkJoin, pipe, tap } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { isDeployed } from '../../../core/lab-deployment';
import { RequestState, initialRequestState } from '../../../core/stores/request-status';
import { toDate } from '../../../core/time';
import { ServerHealth, serverHealth } from './server-health';
import { LabStatus } from '../../../enums/lab-status.enum';
import { Deployment, NewServer, Server, ServerEdit, ServerSettings, ServersService } from './servers.service';

interface ServersState extends RequestState {
  servers: Server[];
  deployments: Deployment[];
  selectedId: string | null;
  loadedAt: number | null;
  checking: string | null;
}

export interface ServerCard {
  server: Server;
  health: ServerHealth;
  /** Team labs with containers or VMs on it. */
  deployments: Deployment[];
  /** Every team lab that starts on it, deployed or stopped. */
  labs: Deployment[];
}

/** Every server with its health, capacity and the team labs deployed on it. */
export const ServersStore = signalStore(
  withState<ServersState>({ servers: [], deployments: [], selectedId: null, loadedAt: null, checking: null, ...initialRequestState }),
  withComputed(({ servers, deployments, selectedId, status }) => {
    const cards = computed<ServerCard[]>(() => servers()
      .map(server => ({
        server,
        health: serverHealth(server),
        deployments: deployments().filter(d => d.serverId === server.id && isDeployed(d.status)),
        labs: deployments().filter(d => d.serverId === server.id && d.status !== LabStatus.DELETED)
          .sort((a, b) => Number(isDeployed(b.status)) - Number(isDeployed(a.status)) || a.teamName.localeCompare(b.teamName)),
      }))
      .sort((a, b) => a.server.name.localeCompare(b.server.name)));
    return {
      loading: computed(() => status() === 'idle' || (status() === 'loading' && servers().length === 0)),
      cards,
      selected: computed<ServerCard | null>(() => cards().find(card => card.server.id === selectedId()) ?? cards()[0] ?? null),
      /** The newest stats update across servers. */
      statsUpdatedAt: computed(() => servers()
        .map(server => toDate(server.stats?.updatedAt)?.getTime() ?? 0)
        .reduce((a, b) => Math.max(a, b), 0) || null),
    };
  }),
  withMethods((store, api = inject(ServersService)) => ({
    load: rxMethod<void>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      exhaustMap(() => forkJoin({ servers: api.servers(), deployments: api.deployments() }).pipe(
        tap(({ servers, deployments }) => patchState(store, { servers, deployments, status: 'loaded', loadedAt: Date.now() })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Servers could not be loaded.' });
          return EMPTY;
        }),
      )),
    )),
    select(serverId: string): void {
      patchState(store, { selectedId: serverId });
    },
    validate(server: NewServer): Promise<boolean> {
      return firstValueFrom(api.validate(server));
    },
    async add(server: NewServer): Promise<Server> {
      const added = await firstValueFrom(api.add(server));
      patchState(store, { servers: [...store.servers(), added], selectedId: added.id });
      return added;
    },
    settings(serverId: string): Promise<ServerSettings> {
      return firstValueFrom(api.settings(serverId));
    },
    validateEdit(serverId: string, edit: ServerEdit): Promise<boolean> {
      return firstValueFrom(api.validateEdit(serverId, edit));
    },
    async update(serverId: string, edit: ServerEdit): Promise<Server> {
      const saved = await firstValueFrom(api.update(serverId, edit));
      patchState(store, { servers: store.servers().map(server => (server.id === serverId ? saved : server)) });
      return saved;
    },
    /** Moves a team's lab and reloads, since a fresh copy changes the tracker on both servers. */
    async moveLab(trackerId: string, serverId: string, startFresh: boolean): Promise<Deployment> {
      try {
        const moved = await firstValueFrom(api.moveLab(trackerId, serverId, startFresh));
        patchState(store, { deployments: store.deployments().map(d => (d.id === trackerId ? moved : d)) });
        return moved;
      } catch (error) {
        const deployments = await firstValueFrom(api.deployments()).catch(() => store.deployments());
        patchState(store, { deployments });
        throw error;
      }
    },
    async check(serverId: string): Promise<Server> {
      patchState(store, { checking: serverId });
      try {
        const checked = await firstValueFrom(api.check(serverId));
        patchState(store, { servers: store.servers().map(server => (server.id === serverId ? checked : server)) });
        return checked;
      } finally {
        patchState(store, { checking: null });
      }
    },
    async remove(serverId: string): Promise<void> {
      await firstValueFrom(api.delete(serverId));
      patchState(store, {
        servers: store.servers().filter(server => server.id !== serverId),
        selectedId: store.selectedId() === serverId ? null : store.selectedId(),
      });
    },
  })),
);
