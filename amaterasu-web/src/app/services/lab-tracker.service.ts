import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, map, Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { LabTracker } from '../models/lab-tracker.model';
import { BaseService } from './base.service';
import { EnvironmentService } from './environment.service';

@Injectable({
  providedIn: 'root'
})
export class LabTrackerService extends BaseService {
  private labTrackersSubject = new BehaviorSubject<LabTracker[] | undefined>(undefined);
  labTrackers$: Observable<LabTracker[] | undefined> = this.labTrackersSubject.asObservable();

  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService) {
    super(httpClient);
  }

  fetchLabTrackers(): void {
    this.get<ApiResponse<LabTracker[]>>(`${this.environmentService.settings?.restUrl}/lab-tracker`)
      .pipe(
        map(response => response.data.map(labTracker => new LabTracker(labTracker)))
      )
      .subscribe(labTracker => this.labTrackersSubject.next(labTracker));
  }

  getLabTrackerById(id: string): Observable<LabTracker> {
    return this.get<ApiResponse<LabTracker>>(this.environmentService.settings?.restUrl + '/lab-tracker/' + id)
      .pipe(
        map((response: ApiResponse<LabTracker>) => new LabTracker(response.data))
      );
  }

  setLabTrackers(labTrackers: LabTracker[]): void {
    this.labTrackersSubject.next(labTrackers);
  }
}
