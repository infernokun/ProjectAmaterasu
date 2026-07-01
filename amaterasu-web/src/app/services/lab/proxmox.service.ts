import { Injectable } from '@angular/core';
import { BaseService } from '../base.service';
import { HttpClient } from '@angular/common/http';
import { EnvironmentService } from '../environment.service';
import { map, Observable } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { ProxmoxVM } from '../../models/lab/proxmox-vm.model';
import { ProxmoxNetworkAdapter } from '../../models/lab/proxmox-network-adapter.model';

@Injectable({
  providedIn: 'root'
})
export class ProxmoxService extends BaseService {

  constructor(protected httpClient: HttpClient, private environmentService: EnvironmentService) {
    super(httpClient);
  }

  getVMTemplates(remoteServerId: string): Observable<ProxmoxVM[]> {
    return this.get<ApiResponse<ProxmoxVM[]>>(this.environmentService.settings?.restUrl + '/proxmox/vms',
      { params: { remoteServerId: remoteServerId, template: '1' } }
    )
      .pipe(
        map((response: ApiResponse<ProxmoxVM[]>) => response.data.map((proxmoxVM) => new ProxmoxVM(proxmoxVM)))
      );
  }

  getNetworkAdapters(remoteServerId: string): Observable<ProxmoxNetworkAdapter[]> {
    return this.get<ApiResponse<ProxmoxNetworkAdapter[]>>(this.environmentService.settings?.restUrl + '/proxmox/adapters',
      { params: { remoteServerId: remoteServerId } }
    )
      .pipe(
        map((response: ApiResponse<ProxmoxNetworkAdapter[]>) =>
          response.data.map((adapter) => new ProxmoxNetworkAdapter(adapter)))
      );
  }

  getAvailableIps(remoteServerId: string, bridge: string, count: number = 1): Observable<string[]> {
    return this.get<ApiResponse<string[]>>(this.environmentService.settings?.restUrl + '/proxmox/available-ips',
      { params: { remoteServerId: remoteServerId, bridge: bridge, count: count.toString() } }
    )
      .pipe(
        map((response: ApiResponse<string[]>) => response.data)
      );
  }
}
