import { Injectable } from '@angular/core';
import { EnvironmentService } from '../environment/environment.service';
import { Lab } from '../../models/lab.model';
import { HttpClient } from '@angular/common/http';
import { BaseService } from '../base/base.service';
import { map, Observable } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { LabTracker } from '../../models/lab-tracker.model';

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

  startLab(labId: string, userId?: string): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/start', {
      labId,
      userId
    });
  }

  stopLab(labId: string, userId?: string): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/stop', {
      labId,
      userId
    });
  }

  deleteLab(labId: string, userId?: string, labTrackerId?: string): Observable<ApiResponse<any>> {
    return this.post<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/delete-lab-from-team', {
      labId,
      userId,
      labTrackerId
    });
  }

  viewLogs(labId: string): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/labs/logs/' + labId);
  }
}
