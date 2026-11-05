import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { ServerType } from '../../../enums/server-type.enum';
import { ApiResponse } from '../../../models/api-response.model';
import { EnvironmentService } from '../../../services/environment.service';

export interface ServerStats {
  status: LabStatus | null;
  hostname?: string | null;
  osName?: string | null;
  osVersion?: string | null;
  /** GB. */
  totalRam: number;
  usedRam: number;
  availableRam: number;
  cpu: number;
  cpuUsagePercent: number;
  /** GB. */
  totalDiskSpace: number;
  usedDiskSpace: number;
  availableDiskSpace: number;
  /** Seconds. */
  uptime: number;
  updatedAt?: string | null;
}

export interface Server {
  id: string;
  name: string;
  ipAddress: string;
  port: number | null;
  serverType: ServerType;
  nodeName?: string | null;
  stats: ServerStats | null;
}

/** A team's lab deployed on a server. */
export interface Deployment {
  id: string;
  labName: string;
  labType: string | null;
  teamId: string | null;
  teamName: string;
  status: LabStatus;
  serverId: string | null;
  units: string;
  updatedAt: string | null;
}

export interface NewServer {
  serverType: ServerType;
  name: string;
  ipAddress: string;
  port: number;
  username?: string;
  password?: string;
  apiToken?: string;
  nodeName?: string;
}

@Injectable({ providedIn: 'root' })
export class ServersService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  private url(path: string): string {
    return `${this.env.settings?.restUrl}${path}`;
  }

  servers(): Observable<Server[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/remote-server')).pipe(map(r => (r.data ?? []).map(toServer)));
  }

  deployments(): Observable<Deployment[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/lab-tracker')).pipe(map(r => (r.data ?? []).map(toDeployment)));
  }

  validate(server: NewServer): Observable<boolean> {
    return this.http.post<ApiResponse<boolean>>(this.url('/remote-server/validate'), server).pipe(map(r => !!r.data));
  }

  add(server: NewServer): Observable<Server> {
    return this.http.post<ApiResponse<any>>(this.url('/remote-server'), server).pipe(map(r => toServer(r.data)));
  }

  check(serverId: string): Observable<Server> {
    return this.http.post<ApiResponse<any>>(this.url(`/remote-server/${serverId}/check`), {}).pipe(map(r => toServer(r.data)));
  }

  delete(serverId: string): Observable<unknown> {
    return this.http.delete(this.url(`/remote-server/${serverId}`));
  }
}

export function toServer(raw: any): Server {
  const stats = raw.remoteServerStats;
  return {
    id: raw.id,
    name: raw.name ?? 'Server',
    ipAddress: raw.ipAddress ?? '',
    port: raw.port ?? null,
    serverType: raw.serverType,
    nodeName: raw.nodeName,
    stats: stats ? {
      status: stats.status ?? null,
      hostname: stats.hostname,
      osName: stats.osName,
      osVersion: stats.osVersion,
      totalRam: stats.totalRam ?? 0,
      usedRam: stats.usedRam ?? 0,
      availableRam: stats.availableRam ?? 0,
      cpu: stats.cpu ?? 0,
      cpuUsagePercent: stats.cpuUsagePercent ?? 0,
      totalDiskSpace: stats.totalDiskSpace ?? 0,
      usedDiskSpace: stats.usedDiskSpace ?? 0,
      availableDiskSpace: stats.availableDiskSpace ?? 0,
      uptime: stats.uptime ?? 0,
      updatedAt: stats.updatedAt ?? null,
    } : null,
  };
}

function toDeployment(raw: any): Deployment {
  const vms: any[] = raw.vms ?? [];
  const services: any[] = raw.services ?? [];
  const bridges: string[] = raw.bridges ?? [];
  let units = '';
  if (vms.length) {
    const ids: number[] = vms.map(vm => Number(vm.vmid)).filter(id => !isNaN(id)).sort((a, b) => a - b);
    units = `${vms.length} ${vms.length === 1 ? 'VM' : 'VMs'}${ids.length ? ` · VMID ${ids.length > 1 ? `${ids[0]}-${ids[ids.length - 1]}` : ids[0]}` : ''}`
      + (bridges.length ? ` · ${bridges.join(', ')}` : '');
  } else if (services.length) {
    const running = services.filter(service => service.state === 'running').length;
    units = running === services.length
      ? `${services.length} ${services.length === 1 ? 'container' : 'containers'}`
      : `${running} of ${services.length} running`;
  }
  return {
    id: raw.id,
    labName: raw.labStarted?.name ?? 'Lab',
    labType: raw.labStarted?.labType ?? null,
    teamId: raw.labOwner?.id ?? null,
    teamName: raw.labOwner?.name ?? 'No team',
    status: raw.labStatus,
    serverId: raw.remoteServer?.id ?? null,
    units,
    updatedAt: raw.updatedAt ?? null,
  };
}
