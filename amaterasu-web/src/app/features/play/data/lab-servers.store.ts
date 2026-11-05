import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom, forkJoin } from 'rxjs';
import { ServerType } from '../../../enums/server-type.enum';
import { Lab } from '../../../models/lab/lab.model';
import { RemoteServer } from '../../../models/lab/remote-server.model';
import { getServerType } from '../../../utils/server-lab-type';
import { bestServer } from '../labs/lab-view';
import { PlayApiService } from './play-api.service';

/** Docker hosts and Proxmox nodes a team can start labs on. */
export const LabServersStore = signalStore(
  { providedIn: 'root' },
  withState({ docker: [] as RemoteServer[], proxmox: [] as RemoteServer[], loaded: false }),
  withComputed(({ docker, proxmox }) => ({
    all: computed(() => [...docker(), ...proxmox()]),
  })),
  withMethods((store, api = inject(PlayApiService)) => ({
    async load(force = false): Promise<void> {
      if (store.loaded() && !force) {
        return;
      }
      try {
        const [docker, proxmox] = await firstValueFrom(forkJoin([api.servers(ServerType.DOCKER_HOST), api.servers(ServerType.PROXMOX)]));
        patchState(store, { docker, proxmox, loaded: true });
      } catch {
        patchState(store, { loaded: true });
      }
    },
    serversFor(lab: Lab | undefined): RemoteServer[] {
      return getServerType(lab?.labType!) === ServerType.PROXMOX ? store.proxmox() : store.docker();
    },
    defaultFor(lab: Lab | undefined): RemoteServer | undefined {
      return bestServer(getServerType(lab?.labType!) === ServerType.PROXMOX ? store.proxmox() : store.docker());
    },
  })),
);
