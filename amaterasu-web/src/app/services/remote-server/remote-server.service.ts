import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EnvironmentService } from '../environment/environment.service';
import { BaseService } from '../base/base.service';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { RemoteServer } from '../../models/remote-server.model';

@Injectable({
  providedIn: 'root'
})
export class RemoteServerService extends BaseService {
  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService
  ) {
    super(httpClient);
  }

  /**
   * Retrieves all remote servers.
   */
  getAllServers(): Observable<RemoteServer[]> {
    const url = this.environmentService.settings?.restUrl + '/remote-server';
    return this.get<ApiResponse<RemoteServer[]>>(url).pipe(
      map((response: ApiResponse<RemoteServer[]>) =>
        response.data.map(server => new RemoteServer(server))
      )
    );
  }

  /**
   * Adds a new remote server.
   */
  addServer(server: RemoteServer): Observable<ApiResponse<RemoteServer>> {
    const url = this.environmentService.settings?.restUrl + '/remote-server';
    return this.post<ApiResponse<RemoteServer>>(url, server);
  }

  /**
   * Updates an existing remote server.
   */
  updateServer(server: RemoteServer): Observable<ApiResponse<RemoteServer>> {
    const url = this.environmentService.settings?.restUrl + '/remote-server/' + server.id;
    return this.put<ApiResponse<RemoteServer>>(url, server);
  }

  /**
   * Deletes a remote server by its id.
   */
  deleteServer(serverId: string): Observable<ApiResponse<any>> {
    const url = this.environmentService.settings?.restUrl + '/remote-server/' + serverId;
    return this.delete<ApiResponse<any>>(url);
  }
}
