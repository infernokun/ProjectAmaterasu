import { Injectable } from '@angular/core';
import { BaseService } from './base.service';
import { HttpClient } from '@angular/common/http';
import { EnvironmentService } from './environment.service';
import { ProxmoxVM } from '../models/proxmox-vm.model';
import { map, Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { RemoteServer } from '../models/remote-server.model';

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
}
