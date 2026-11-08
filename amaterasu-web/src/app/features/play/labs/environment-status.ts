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

/**
 * A live tracker change applied to a lab's environment: the same copy, a new shared copy for this
 * lab, or a new copy for the viewer's team. Returns the same object when the change is not for it.
 */
export function withTrackerChange(
  environment: EnvironmentView,
  labId: string,
  tracker: LabTracker,
  removed: boolean,
  teamId: string | null | undefined,
): EnvironmentView {
  const same = !!environment.trackerId && environment.trackerId === tracker.id;
  const fresh = !environment.trackerId && tracker.labStarted?.id === environment.labId
    && (environment.mode === 'SHARED' ? tracker.roomId === labId
      : !tracker.roomId && !!teamId && tracker.labOwner?.id === teamId);
  if (!same && !fresh) return environment;
  if (removed || tracker.labStatus === LabStatus.DELETED) {
    return { ...environment, trackerId: null, tracker: null, status: 'NOT_RUNNING' };
  }
  return { ...environment, trackerId: tracker.id ?? null, tracker, status: environmentStatus(tracker) };
}
