import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, map, Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { LabTracker } from '../models/lab-tracker.model';
import { BaseService } from './base.service';
import { EnvironmentService } from './environment.service';

@Injectable({
  providedIn: 'root'
})
export class LabTrackerService extends BaseService {
  private labTrackersSubject = new BehaviorSubject<LabTracker[]>([]);
  labTrackers$: Observable<LabTracker[]> = this.labTrackersSubject.asObservable();

  private labTrackersByTeamSubject = new BehaviorSubject<LabTracker[]>([]);
  labTrackersByTeam$: Observable<LabTracker[]> = this.labTrackersSubject.asObservable();

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
      .subscribe((labTrackers: LabTracker[]) => this.labTrackersSubject.next(labTrackers));
  }

  getLabTrackersByTeam(teamId: string): void {
    this.get<ApiResponse<LabTracker[]>>(this.environmentService.settings?.restUrl + '/lab-tracker', { params: new HttpParams().set('teamId', teamId) })
    .pipe(
      map((response: ApiResponse<LabTracker[]>) => response.data.map((labTracker: LabTracker) => new LabTracker(labTracker)))
    ).subscribe((labTrackers: LabTracker[]) => this.labTrackersByTeamSubject.next(labTrackers))
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
