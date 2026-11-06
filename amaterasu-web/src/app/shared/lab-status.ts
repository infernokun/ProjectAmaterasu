import { LabStatus } from '../enums/lab-status.enum';

/** States that exist only in the client while a lab request is in flight. */
export type LabTransition = 'STARTING' | 'PAUSING' | 'RESUMING' | 'STOPPING' | 'RESTARTING' | 'REMOVING';

export type LabDisplayState = LabStatus | LabTransition;

/** Color families from the design system: s-active, s-work, s-paused, s-warn, s-bad, s-off, s-accent. */
export type StatusTone = 'active' | 'work' | 'paused' | 'warn' | 'bad' | 'off' | 'accent';

export interface StatusMeta {
  label: string;
  tone: StatusTone;
  /** A request is running; show a spinner instead of a dot. */
  working: boolean;
}

const meta = (label: string, tone: StatusTone, working = false): StatusMeta => ({ label, tone, working });

export const LAB_STATE_META: Record<LabDisplayState, StatusMeta> = {
  [LabStatus.NONE]: meta('Not started', 'off'),
  [LabStatus.ACTIVE]: meta('Active', 'active'),
  [LabStatus.INACTIVE]: meta('Inactive', 'off'),
  [LabStatus.HIDDEN]: meta('Hidden', 'off'),
  [LabStatus.ONLINE]: meta('Online', 'active'),
  [LabStatus.OFFLINE]: meta('Offline', 'bad'),
  [LabStatus.DELETED]: meta('Removed', 'off'),
  [LabStatus.STOPPED]: meta('Stopped', 'off'),
  [LabStatus.PAUSED]: meta('Paused', 'paused'),
  [LabStatus.RETIRED]: meta('Retired', 'off'),
  [LabStatus.FAILED]: meta('Failed', 'bad'),
  [LabStatus.DEGRADED]: meta('Degraded', 'warn'),
  [LabStatus.ACTIVE_FAILED]: meta('Active with failures', 'warn'),
  [LabStatus.FAILED_ACTIVE]: meta('Recovered', 'warn'),
  STARTING: meta('Starting', 'work', true),
  PAUSING: meta('Pausing', 'work', true),
  RESUMING: meta('Resuming', 'work', true),
  STOPPING: meta('Stopping', 'work', true),
  RESTARTING: meta('Restarting', 'work', true),
  REMOVING: meta('Removing', 'work', true),
};

export function labStateMeta(state: LabDisplayState | null | undefined): StatusMeta {
  return (state && LAB_STATE_META[state]) || LAB_STATE_META[LabStatus.NONE];
}
