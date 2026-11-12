import { inject } from '@angular/core';
import { CanActivateFn, Params, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { ProxmoxVM } from '../../../models/lab/proxmox-vm.model';
import { LabCard } from '../data/play.models';
import { PlayApiService } from '../data/play-api.service';
import { isVm } from './lab-view';

/** A router link split into what `routerLink` and `queryParams` take. */
export interface LabLink {
  commands: unknown[];
  queryParams: Params;
}

/**
 * A lab's pages live under /labs/<lab>. A copy is a tracker, and a lab has several (per team, Solo,
 * shared), so the copy goes in ?copy=; without it the page shows the viewer's own copy.
 */
export function labDetailsLink(labId: string, copy?: string | null): LabLink {
  return { commands: ['/labs', labId, 'details'], queryParams: copy ? { copy } : {} };
}

export function labConsoleLink(labId: string, vmid: number | string | undefined, copy?: string | null): LabLink {
  return { commands: ['/labs', labId, 'console', vmid], queryParams: copy ? { copy } : {} };
}

/** The VM a console opens on first: the running bastion, else the first running VM. */
export function consoleVm(tracker: LabTracker | null | undefined): ProxmoxVM | null {
  if (!tracker || !isVm(tracker.labStarted)) return null;
  const running = (tracker.vms ?? []).filter(vm => vm.status === 'running' && vm.vmid != null);
  return running.find(vm => vm.role === 'bastion') ?? running[0] ?? null;
}

/**
 * The lab a copy belongs to. A shared copy names its lab; a team's or the Solo copy only names its
 * environment, which several labs can run, so prefer the lab whose own copy it is, then the current lab.
 */
export function labForCopy(tracker: LabTracker, cards: LabCard[], currentLabId?: string | null): string | null {
  if (tracker.roomId) return tracker.roomId;
  const environmentId = tracker.labStarted?.id;
  if (!environmentId) return null;
  const using = cards.filter(card => card.environment?.labId === environmentId);
  return (using.find(card => card.environment?.trackerId === tracker.id)
    ?? using.find(card => card.id === currentLabId)
    ?? using[0])?.id ?? null;
}

/**
 * /environments/<copy>[/console/<vmid>] moved under the copy's lab. A copy no lab runs (a test
 * deployment from Build) keeps the old standalone page.
 */
export const copyAddressGuard: CanActivateFn = async route => {
  const router = inject(Router);
  const api = inject(PlayApiService);
  const rooms = inject(RoomsStore);
  const trackerId = route.paramMap.get('trackerId')!;
  const vmid = route.paramMap.get('vmid');
  let labId: string | null = null;
  try {
    const tracker = await firstValueFrom(api.tracker(trackerId));
    labId = tracker.roomId ?? labForCopy(tracker, await firstValueFrom(api.cards()), rooms.currentRoom()?.id);
  } catch {
    // The standalone page explains what went wrong
  }
  if (!labId) return true;
  const link = vmid ? labConsoleLink(labId, vmid, trackerId) : labDetailsLink(labId, trackerId);
  return router.createUrlTree(link.commands, { queryParams: link.queryParams });
};
