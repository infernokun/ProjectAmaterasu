import { LabStatus } from '../../../enums/lab-status.enum';
import { ServerType } from '../../../enums/server-type.enum';
import { StatusTone } from '../../../shared/lab-status';
import { Server } from './servers.service';

export const BUSY_CPU = 85;

export interface Meter {
  label: string;
  pct: number;
  /** Bar color class: ok, warn, bad or off. */
  level: 'ok' | 'warn' | 'bad' | 'off';
  text: string;
}

export interface ServerHealth {
  tone: StatusTone;
  label: 'Online' | 'Busy' | 'Offline' | 'Not checked';
  online: boolean;
  busy: boolean;
  cpu: Meter;
  memory: Meter;
  disk: Meter;
}

const level = (pct: number): Meter['level'] => (pct >= 85 ? 'bad' : pct >= 70 ? 'warn' : 'ok');
const pct = (used: number, total: number) => (total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0);
const noData = (label: string): Meter => ({ label, pct: 0, level: 'off', text: 'no data' });

/** Sizes from the stats script are GB: "38 of 64 GB", or "1.1 of 4 TB" for big disks. */
export function usage(usedGb: number, totalGb: number): string {
  if (totalGb >= 1000) {
    const tb = (gb: number) => String(Math.round(gb / 100) / 10);
    return `${tb(usedGb)} of ${tb(totalGb)} TB`;
  }
  return `${Math.round(usedGb)} of ${Math.round(totalGb)} GB`;
}

export function serverHealth(server: Server): ServerHealth {
  const stats = server.stats;
  if (!stats || !stats.status) {
    return { tone: 'off', label: 'Not checked', online: false, busy: false, cpu: noData('CPU'), memory: noData('Memory'), disk: noData('Disk') };
  }
  const online = stats.status === LabStatus.ACTIVE;
  if (!online) {
    return { tone: 'bad', label: 'Offline', online, busy: false, cpu: noData('CPU'), memory: noData('Memory'), disk: noData('Disk') };
  }
  // Proxmox nodes report reachability only
  const measured = stats.totalRam > 0 || stats.cpu > 0;
  const busy = measured && stats.cpuUsagePercent >= BUSY_CPU;
  const cpuPct = Math.round(stats.cpuUsagePercent);
  const memPct = pct(stats.usedRam, stats.totalRam);
  const diskPct = pct(stats.usedDiskSpace, stats.totalDiskSpace);
  return {
    tone: busy ? 'warn' : 'active',
    label: busy ? 'Busy' : 'Online',
    online,
    busy,
    cpu: measured ? { label: 'CPU', pct: cpuPct, level: level(cpuPct), text: `${cpuPct}% of ${stats.cpu}` } : noData('CPU'),
    memory: measured
      ? { label: 'Memory', pct: memPct, level: level(memPct), text: usage(stats.usedRam, stats.totalRam) }
      : noData('Memory'),
    disk: measured && stats.totalDiskSpace > 0
      ? { label: 'Disk', pct: diskPct, level: level(diskPct), text: usage(stats.usedDiskSpace, stats.totalDiskSpace) }
      : noData('Disk'),
  };
}

export function serverTypeLabel(type: ServerType): string {
  return type === ServerType.PROXMOX ? 'Proxmox node' : 'Docker host';
}

export function address(server: Server): string {
  return server.port ? `${server.ipAddress}:${server.port}` : server.ipAddress;
}

/** "12 days", "3 h", "40 min". */
export function uptime(seconds: number): string {
  if (!seconds) {
    return 'unknown';
  }
  const days = Math.floor(seconds / 86400);
  if (days >= 1) {
    return `${days} ${days === 1 ? 'day' : 'days'}`;
  }
  const hours = Math.floor(seconds / 3600);
  return hours >= 1 ? `${hours} h` : `${Math.max(1, Math.floor(seconds / 60))} min`;
}
