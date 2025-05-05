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
  labTrackersByTeam$: Observable<LabTracker[]> = this.labTrackersByTeamSubject.asObservable();

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

  getLabTrackersByTeam(teamId: string): Observable<LabTracker[]> {
    const params = new HttpParams().set('teamId', teamId);
    return this.get<ApiResponse<LabTracker[]>>(this.environmentService.settings?.restUrl + '/lab-tracker', { params })
    .pipe(
      map((response: ApiResponse<LabTracker[]>) => response.data.map((labTracker: LabTracker) => new LabTracker(labTracker)))
    );
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

  setLabTrackersByTeam(labTrackers: LabTracker[]): void {
    this.labTrackersByTeamSubject.next(labTrackers);
  }

  getSettings(labTrackerId: string, remoteServerId: string): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/lab-tracker/settings/' + labTrackerId + '/' + remoteServerId);
  }

  getLogs(labTrackerId: string, remoteServerId: string, service: string = ""): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + '/lab-tracker/logs/' + labTrackerId + '/' + remoteServerId + '/' + service);
  }

  uploadVolumeFiles(labTrackerId: string, remoteServerId: string, formData: FormData) {
    return this.post<ApiResponse<boolean>>(this.environmentService.settings?.restUrl + '/lab-tracker/settings/' + labTrackerId + '/' + remoteServerId, formData);
  }
}
