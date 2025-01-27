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
}
