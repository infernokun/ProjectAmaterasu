import { formatDistanceToNowStrict } from 'date-fns';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { DockerServiceInfo } from '../../../models/lab/docker-service-info.model';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { Lab } from '../../../models/lab/lab.model';
import { ProxmoxVM } from '../../../models/lab/proxmox-vm.model';
import { RemoteServer } from '../../../models/lab/remote-server.model';
import { LabDisplayState, StatusTone, labStateMeta } from '../../../shared/lab-status';

/** Wording and allowed actions for one team lab, shared by Home, Labs and the lab console. */

export const isVm = (lab: Lab | undefined) => lab?.labType === LabType.VIRTUAL_MACHINE;

export function labTypeLabel(lab: Lab | undefined): string {
  switch (lab?.labType) {
    case LabType.VIRTUAL_MACHINE: return 'Proxmox VM';
    case LabType.DOCKER_COMPOSE: return 'Docker Compose';
    case LabType.DOCKER_CONTAINER: return 'Docker container';
    case LabType.KUBERNETES: return 'Kubernetes';
    default: return 'Lab';
  }
}

const RUNNING = new Set<LabDisplayState>([LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE]);

export const isRunningState = (state: LabDisplayState) => RUNNING.has(state);

/** The lab's way in from the LAN, if it has one. */
export function bastionOf(tracker: LabTracker | null | undefined): ProxmoxVM | null {
  return (tracker?.vms ?? []).find(vm => vm.role === 'bastion') ?? null;
}

/** The cloud-init user the copy's password is for: the bastion's, else the first VM that has one. */
export function loginUser(tracker: LabTracker | null | undefined): string | null {
  const bastion = bastionOf(tracker);
  return bastion?.ciuser ?? (tracker?.vms ?? []).find(vm => vm.ciuser)?.ciuser ?? null;
}

/** A running VM whose guest agent has not answered yet is still starting up. */
export const startingUp = (vm: ProxmoxVM) => vm.status === 'running' && !!vm.guestAgent && vm.ready === false;

export function vmLook(vm: ProxmoxVM): { tone: StatusTone; label: string } {
  if (vm.status === 'running') {
    if (startingUp(vm)) return { tone: 'work', label: 'Starting up' };
    return { tone: 'active', label: vm.guestAgent && vm.ready ? 'Ready' : 'running' };
  }
  if (vm.status === 'paused') return { tone: 'paused', label: 'paused' };
  return { tone: 'off', label: vm.status ?? 'unknown' };
}

export interface LabCardActions {
  canOpen: boolean;
  canPause: boolean;
  canResume: boolean;
  canStartFresh: boolean;
  canStop: boolean;
  canRemove: boolean;
  canRestart: boolean;
  working: boolean;
}

export function labActions(tracker: LabTracker, state: LabDisplayState): LabCardActions {
  const working = labStateMeta(state).working;
  const running = RUNNING.has(state);
  const compose = tracker.labStarted?.labType === LabType.DOCKER_COMPOSE;
  return {
    canOpen: !working,
    canPause: !working && running,
    canResume: !working && state === LabStatus.PAUSED,
    canStartFresh: !working && (state === LabStatus.STOPPED || state === LabStatus.FAILED || state === LabStatus.NONE),
    canStop: !working && (running || state === LabStatus.PAUSED),
    canRemove: !working && (state === LabStatus.STOPPED || state === LabStatus.FAILED || state === LabStatus.PAUSED || state === LabStatus.NONE),
    canRestart: !working && compose && state === LabStatus.DEGRADED && failedServices(tracker).length > 0,
    working,
  };
}

/**
 * What the viewer's role allows of those actions: admins and facilitators run the lifecycle, only
 * admins take a copy off its team, and everyone else only opens it.
 */
export function allowedActions(actions: LabCardActions, canRun: boolean, isAdmin: boolean): LabCardActions {
  if (canRun) return { ...actions, canRemove: actions.canRemove && isAdmin };
  return { ...actions, canPause: false, canResume: false, canStartFresh: false, canStop: false, canRemove: false, canRestart: false };
}

export type LifeStep = 'run' | 'pause' | 'stop';

/** Which lifecycle step the lab is at, for the Running, Paused, Stopped line. */
export function lifeStep(state: LabDisplayState): LifeStep | null {
  if (RUNNING.has(state) || state === 'STARTING' || state === 'RESUMING' || state === 'RESTARTING') return 'run';
  if (state === LabStatus.PAUSED || state === 'PAUSING') return 'pause';
  if (state === LabStatus.STOPPED || state === 'STOPPING') return 'stop';
  return null;
}

export function toneOf(state: LabDisplayState): StatusTone {
  return labStateMeta(state).tone;
}

export function failedServices(tracker: LabTracker): DockerServiceInfo[] {
  return (tracker.services ?? []).filter(service => service.state !== 'running');
}

/** The compose service name: the label when the API sends it, otherwise taken from the container name. */
export function serviceName(tracker: LabTracker, service: DockerServiceInfo): string {
  if (service.service) {
    return service.service;
  }
  const prefix = `${tracker.id}-`;
  const name = service.name.startsWith(prefix) ? service.name.slice(prefix.length) : service.name;
  return name.replace(/-\d+$/, '');
}

export function unitCounts(tracker: LabTracker): { up: number; total: number; noun: string } {
  if (isVm(tracker.labStarted)) {
    const vms = tracker.vms ?? [];
    return { up: vms.filter(vm => vm.status === 'running').length, total: vms.length, noun: 'VMs' };
  }
  const services = tracker.services ?? [];
  return { up: services.filter(service => service.state === 'running').length, total: services.length, noun: 'services' };
}

/** The one-line explanation under a lab's name. */
export function labDetail(tracker: LabTracker, state: LabDisplayState): string {
  const vm = isVm(tracker.labStarted);
  const { up, total, noun } = unitCounts(tracker);
  const server = tracker.remoteServer?.name ?? 'the server';
  switch (state) {
    case LabStatus.ACTIVE: {
      if (total === 0) return vm ? 'Running. Reach the VMs through the Proxmox console.' : 'Running.';
      if (!vm) return `${up} of ${total} services running.`;
      const starting = (tracker.vms ?? []).filter(startingUp).length;
      const reach = bastionOf(tracker) ? 'SSH in through the bastion or use the console.' : 'Reach them through the Proxmox console.';
      return `${up} of ${total} VMs running${starting ? `, ${starting} starting up` : ''}. ${reach}`;
    }
    case LabStatus.DEGRADED: {
      const down = failedServices(tracker).map(service => serviceName(tracker, service));
      return total
        ? `${up} of ${total} ${noun} running.${down.length ? ` ${down.join(', ')} ${down.length === 1 ? 'is' : 'are'} not running.` : ''}`
        : 'Some parts of this lab are not running.';
    }
    case LabStatus.PAUSED:
      return vm ? 'VMs shut down, disks kept. Resume picks up where your team left off.'
        : 'Containers kept. Resume picks up where your team left off.';
    case LabStatus.STOPPED:
      return 'This copy was destroyed. Starting again builds a fresh one.';
    case LabStatus.FAILED:
      return `The last action failed on ${server}. Start a fresh copy, or remove it from your team.`;
    case LabStatus.NONE:
      return 'Not started yet.';
    default:
      return labStateMeta(state).working ? `Working on ${server}. This usually takes under a minute.` : '';
  }
}

/** What Stop destroys, for the confirmation. */
export function stopWarning(tracker: LabTracker): string {
  return isVm(tracker.labStarted)
    ? 'The cloned VMs and their bridges are destroyed.'
    : 'Containers, volumes and uploaded files are removed.';
}

export function pauseHelp(tracker: LabTracker): string {
  return isVm(tracker.labStarted) ? 'Shuts the VMs down and keeps their disks' : 'Stops the containers and keeps them';
}

export function changedAgo(tracker: LabTracker): string {
  const at = tracker.updatedAt ?? tracker.createdAt;
  return at ? `updated ${formatDistanceToNowStrict(at, { addSuffix: true })}` : '';
}

/** The ACTIVE server of the right kind with the most free memory, skipping servers taking no new labs. */
export function bestServer(servers: RemoteServer[]): RemoteServer | undefined {
  const active = servers.filter(server => server.remoteServerStats?.status === LabStatus.ACTIVE && !server.noNewLabs);
  return [...active].sort((a, b) => (b.remoteServerStats?.availableRam ?? 0) - (a.remoteServerStats?.availableRam ?? 0))[0];
}

export function shortId(id: string | undefined): string {
  return id ? id.slice(0, 8) : '';
}
