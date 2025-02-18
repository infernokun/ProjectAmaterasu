import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { LabTracker } from '../models/lab-tracker.model';
import { BaseService } from './base.service';
import { EnvironmentService } from './environment.service';

@Injectable({
  providedIn: 'root'
})
export class LabTrackerService extends BaseService {

  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService) {
    super(httpClient);
  }

  getAllLabTrackers(): Observable<LabTracker[]> {
    return this.get<ApiResponse<LabTracker[]>>(this.environmentService.settings?.restUrl + '/lab-tracker')
      .pipe(
        map((response: ApiResponse<LabTracker[]>) => response.data.map((labTracker) => new LabTracker(labTracker)))
      );
  }

  getLabTrackerById(id: string): Observable<LabTracker> {
    return this.get<ApiResponse<LabTracker>>(this.environmentService.settings?.restUrl + '/lab-tracker/' + id)
      .pipe(
        map((response: ApiResponse<LabTracker>) => new LabTracker(response.data))
      );
  }
}
