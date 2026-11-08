import { LabStatus } from '../../../enums/lab-status.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { StatusTone } from '../../../shared/lab-status';
import { EnvironmentStatus, EnvironmentView } from '../data/play.models';
import { startingUp } from './lab-view';

const UP = new Set<string>([LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE]);
const STARTING = new Set<string>(['STARTING', 'RESUMING']);

/**
 * One status for a copy, the same rule as the API's EnvironmentStatuses, so the Labs list, the lab
 * page and admin views agree. `pending` is a request this client has in flight.
 */
export function environmentStatus(tracker: LabTracker | null | undefined, pending?: string | null): EnvironmentStatus {
  const action = pending ?? tracker?.pendingAction;
  if (action && STARTING.has(action)) return 'STARTING';
  if (!tracker) return 'NOT_RUNNING';
  const status = tracker.labStatus;
  if (status && UP.has(status)) return (tracker.vms ?? []).some(startingUp) ? 'STARTING' : 'RUNNING';
  if (status === LabStatus.PAUSED) return 'PAUSED';
  if (status === LabStatus.FAILED) return 'FAILED';
  return 'NOT_RUNNING';
}

export const ENVIRONMENT_STATUS_META: Record<EnvironmentStatus, { label: string; tone: StatusTone; working: boolean }> = {
  NOT_RUNNING: { label: 'Not running', tone: 'off', working: false },
  STARTING: { label: 'Starting', tone: 'work', working: true },
  RUNNING: { label: 'Running', tone: 'active', working: false },
  PAUSED: { label: 'Paused', tone: 'paused', working: false },
  FAILED: { label: 'Failed', tone: 'bad', working: false },
};

export function trackerOf(environment: EnvironmentView | null | undefined): LabTracker | null {
  return environment?.tracker ? new LabTracker(environment.tracker) : null;
}

/** Whether a new tracker is the given copy: the lab's shared one, the Solo one, or a team's. */
function isCopy(target: string, labId: string, tracker: LabTracker): boolean {
  if (target === 'shared') return tracker.roomId === labId;
  if (target === 'solo') return !tracker.roomId && !!tracker.solo;
  return !tracker.roomId && !tracker.solo && target === `team:${tracker.labOwner?.id}`;
}

/**
 * A live tracker change applied to a lab's environment: the viewer's copy and, for those who run the
 * lab, its list of copies. The viewer's copy is the shared one, their team's, or the Solo copy when
 * they have no team. Returns the same object when the change is not for it.
 */
export function withTrackerChange(
  environment: EnvironmentView,
  labId: string,
  tracker: LabTracker,
  removed: boolean,
  teamId: string | null | undefined,
): EnvironmentView {
  if (tracker.labStarted?.id && tracker.labStarted.id !== environment.labId) return environment;
  const gone = removed || tracker.labStatus === LabStatus.DELETED;
  let changed = false;

  const copies = (environment.copies ?? []).map(copy => {
    const same = !!copy.trackerId && copy.trackerId === tracker.id;
    const fresh = !copy.trackerId && isCopy(copy.target, labId, tracker);
    if (!same && !fresh) return copy;
    changed = true;
    return gone ? { ...copy, trackerId: null, tracker: null, status: 'NOT_RUNNING' as const }
      : { ...copy, trackerId: tracker.id ?? null, tracker, status: environmentStatus(tracker) };
  });

  const own = environment.mode === 'SHARED' ? 'shared' : teamId ? `team:${teamId}` : 'solo';
  const same = !!environment.trackerId && environment.trackerId === tracker.id;
  const fresh = !environment.trackerId && isCopy(own, labId, tracker);
  let next: EnvironmentView = changed ? { ...environment, copies } : environment;
  if (same || fresh) {
    next = gone ? { ...next, trackerId: null, tracker: null, status: 'NOT_RUNNING' }
      : { ...next, trackerId: tracker.id ?? null, tracker, status: environmentStatus(tracker) };
  }
  return next;
}
