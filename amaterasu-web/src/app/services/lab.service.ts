import { Injectable } from '@angular/core';
import { Lab } from '../models/lab.model';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { BaseService } from './base.service';
import { EnvironmentService } from './environment.service';
import { LabRequest } from '../models/dto/lab-request.model';
import { LabActionResult } from '../models/lab-action-result.model';

const httpOptions = {
  headers: new HttpHeaders({
    'Content-Type': 'application/json',
  }),
};

@Injectable({
  providedIn: 'root'
})
export class LabService extends BaseService {

  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService) {
    super(httpClient);
  }

  getAllLabs(): Observable<Lab[]> {
    return this.get<ApiResponse<Lab[]>>(this.environmentService.settings?.restUrl + '/labs')
      .pipe(
        map((response: ApiResponse<Lab[]>) => response.data.map((lab) => new Lab(lab)))
      );
  }

  getLabById(labId: string): Observable<Lab> {
    return this.get<ApiResponse<Lab>>(this.environmentService.settings?.restUrl + '/labs/' + labId)
      .pipe(
        map((response: ApiResponse<Lab>) => new Lab(response.data))
      );
  }

  startLab(labRequest: LabRequest): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<LabActionResult>>(this.environmentService.settings?.restUrl + '/labs/start',
      labRequest
    );
  }

  stopLab(labId: string, userId?: string, labTrackerId?: string): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/stop', {
      labId,
      userId,
      labTrackerId
    });
  }

  createNewLab(lab: Lab, remoteServerId: string): Observable<ApiResponse<Lab>> {
    const httpOptions = {
      params: new HttpParams().set('remoteServerId', remoteServerId),
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    };
    const params = new HttpParams().set('remoteServerId', remoteServerId);
    return this.post<ApiResponse<Lab>>(this.environmentService.settings?.restUrl + '/labs', lab, httpOptions
    )
  }

  deleteLab(labId: string, userId?: string, labTrackerId?: string): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/delete-lab-from-team', {
      labId,
      userId,
      labTrackerId
    });
  }

  uploadDockerComposeFile(labId: string, content: string): Observable<ApiResponse<string>> {
    return this.post<ApiResponse<string>>(this.environmentService.settings?.restUrl + '/labs/upload/' + labId,
      content
    );
  }

  getLabReadiness(labId: string): Observable<ApiResponse<boolean>> {
    return this.get<ApiResponse<boolean>>(this.environmentService.settings?.restUrl + '/labs/check/' + labId);
  }

  viewLogs(labId: string): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/logs/' + labId);
  }

  getSettings(labId: string): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/settings/' + labId);
  }

  clear(teamId: string): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/dev', {
      teamId
    });
  }
}
