import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';
import { ApiResponse } from '../../../models/api-response.model';
import { EnvironmentService } from '../../../services/environment.service';
import { Server, toServer } from '../../admin/servers/servers.service';

export interface CatalogLab {
  id: string;
  name: string;
  description: string;
  version: string;
  capacity: number;
  labType: LabType;
  ready: boolean;
  /** Drafts are hidden from teams and cannot start. */
  published: boolean;
  vmIds: number[];
  createdBy: string | null;
  updatedAt: string | null;
}

export interface LabRequest {
  name: string;
  description: string;
  version: string;
  labType: LabType;
  capacity: number;
  dockerFile?: string;
  vms?: number[];
}

export interface VmTemplate {
  vmid: number;
  name: string;
  cpus: number;
  /** Bytes. */
  maxmem: number;
}

export interface NodeNetwork {
  iface: string;
  address: string | null;
  cidr: string | null;
}

/** Lab catalog authoring: labs, their compose files and the Proxmox templates they clone. */
@Injectable({ providedIn: 'root' })
export class LabBuilderService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  private url(path: string): string {
    return `${this.env.settings?.restUrl}${path}`;
  }

  labs(): Observable<CatalogLab[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/labs')).pipe(map(r => (r.data ?? []).map(toLab)));
  }

  lab(labId: string): Observable<CatalogLab> {
    return this.http.get<ApiResponse<any>>(this.url(`/labs/${labId}`)).pipe(map(r => toLab(r.data)));
  }

  servers(): Observable<Server[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/remote-server')).pipe(map(r => (r.data ?? []).map(toServer)));
  }

  templates(serverId: string): Observable<VmTemplate[]> {
    return this.http
      .get<ApiResponse<any[]>>(this.url('/proxmox/vms'), { params: { remoteServerId: serverId, template: 'true' } })
      .pipe(map(r => (r.data ?? []).map(vm => ({ vmid: vm.vmid, name: vm.name ?? `VM ${vm.vmid}`, cpus: vm.cpus ?? 0, maxmem: vm.maxmem ?? 0 }))));
  }

  /** The template's network cards, as net0 to "virtio=...,bridge=vmbr1". */
  networkCards(serverId: string, vmid: number): Observable<Record<string, string>> {
    return this.http
      .get<ApiResponse<any>>(this.url('/proxmox/config'), { params: { remoteServerId: serverId, vmid: String(vmid) } })
      .pipe(map(r => r.data?.networks ?? {}));
  }

  networks(serverId: string): Observable<NodeNetwork[]> {
    return this.http
      .get<ApiResponse<any[]>>(this.url('/proxmox/networks'), { params: { remoteServerId: serverId } })
      .pipe(map(r => (r.data ?? []).map(n => ({ iface: n.iface, address: n.address ?? null, cidr: n.cidr ?? null }))));
  }

  /** True when the API can parse the file as YAML. */
  validateYaml(content: string): Observable<boolean> {
    return this.http
      .post<ApiResponse<boolean>>(this.url('/labs/validate'), content, { headers: { 'Content-Type': 'text/plain' } })
      .pipe(map(r => !!r.data));
  }

  /** Runs docker compose config against the saved file on the server. */
  checkOnServer(labId: string, serverId: string): Observable<boolean> {
    return this.http.get<ApiResponse<boolean>>(this.url(`/labs/check/${labId}/${serverId}`)).pipe(map(r => !!r.data));
  }

  composeFile(labId: string, serverId: string): Observable<string> {
    return this.http
      .get<ApiResponse<{ yml?: string }>>(this.url(`/labs/settings/${labId}/${serverId}`))
      .pipe(map(r => r.data?.yml ?? ''));
  }

  uploadCompose(labId: string, serverId: string, content: string): Observable<unknown> {
    return this.http.post(this.url(`/labs/upload/${labId}/${serverId}`), content, { headers: { 'Content-Type': 'text/plain' } });
  }

  create(request: LabRequest, serverId: string): Observable<CatalogLab> {
    return this.http
      .post<ApiResponse<any>>(this.url('/labs'), request, { params: { remoteServerId: serverId } })
      .pipe(map(r => toLab(r.data)));
  }

  update(labId: string, request: Partial<LabRequest>): Observable<CatalogLab> {
    return this.http.put<ApiResponse<any>>(this.url(`/labs/${labId}`), request).pipe(map(r => toLab(r.data)));
  }

  publish(labId: string): Observable<CatalogLab> {
    return this.http.put<ApiResponse<any>>(this.url(`/labs/${labId}/publish`), {}).pipe(map(r => toLab(r.data)));
  }

  /** Refused with 409 while any team has the lab deployed. */
  unpublish(labId: string): Observable<CatalogLab> {
    return this.http.put<ApiResponse<any>>(this.url(`/labs/${labId}/unpublish`), {}).pipe(map(r => toLab(r.data)));
  }

  delete(labId: string): Observable<unknown> {
    return this.http.delete(this.url(`/labs/${labId}`));
  }
}

function toLab(raw: any): CatalogLab {
  return {
    id: raw.id,
    name: raw.name ?? 'Untitled lab',
    description: raw.description ?? '',
    version: raw.version ?? '',
    capacity: raw.capacity ?? 1,
    labType: raw.labType,
    ready: !!raw.ready,
    published: raw.published === true,
    vmIds: raw.vmIds ?? [],
    createdBy: raw.createdBy ?? null,
    updatedAt: raw.updatedAt ?? null,
  };
}
