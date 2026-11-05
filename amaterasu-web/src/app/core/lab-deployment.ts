import { LabStatus } from '../enums/lab-status.enum';

const NOT_DEPLOYED = new Set<string>([LabStatus.STOPPED, LabStatus.DELETED, LabStatus.NONE]);
const RUNNING = new Set<string>([LabStatus.ACTIVE, LabStatus.DEGRADED, LabStatus.ACTIVE_FAILED, LabStatus.FAILED_ACTIVE]);

/** The lab still has containers or VMs on its server. The API refuses to delete what it runs on. */
export function isDeployed(status: string | null | undefined): boolean {
  return !!status && !NOT_DEPLOYED.has(status);
}

/** The lab is up, even if some of it is unhealthy. */
export function isRunning(status: string | null | undefined): boolean {
  return !!status && RUNNING.has(status);
}
