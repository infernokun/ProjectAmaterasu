import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ServerType } from '../../../enums/server-type.enum';
import { ApiResponse } from '../../../models/api-response.model';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { RemoteServer } from '../../../models/lab/remote-server.model';
import { Team } from '../../../models/team.model';
import { EnvironmentService } from '../../../services/environment.service';
import { AnswerResult, LabAccess, LabChallenge, LabFiles, RoomLive, RoomOverview, RoomStandings } from './play.models';

/** HTTP for the Play and Run screens. */
@Injectable({ providedIn: 'root' })
export class PlayApiService {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(EnvironmentService);

  private url(path: string): string {
    return `${this.environment.settings?.restUrl}${path}`;
  }

  private data<T>(request: Observable<ApiResponse<T>>): Observable<T> {
    return request.pipe(map(response => response.data));
  }

  overview(roomId: string): Observable<RoomOverview> {
    return this.data(this.http.get<ApiResponse<RoomOverview>>(this.url(`/room/${roomId}/overview`)));
  }

  standings(roomId: string): Observable<RoomStandings> {
    return this.data(this.http.get<ApiResponse<RoomStandings>>(this.url(`/room/${roomId}/standings`)));
  }

  live(roomId: string): Observable<RoomLive> {
    return this.data(this.http.get<ApiResponse<RoomLive>>(this.url(`/room/${roomId}/live`)));
  }

  submitFlag(roomId: string, challengeId: string, flag: string): Observable<AnswerResult> {
    return this.data(this.http.post<ApiResponse<AnswerResult>>(this.url('/answer'), { roomId, questionId: challengeId, flag }));
  }

  unlockHint(roomId: string, userId: string, challengeId: string, hintId: string): Observable<unknown> {
    const params = new HttpParams().set('hintId', hintId).set('roomId', roomId).set('userId', userId).set('ctfEntityId', challengeId);
    return this.data(this.http.post<ApiResponse<unknown>>(this.url('/ctf-entity/use-hint'), {}, { params }));
  }

  awardPoints(roomId: string, userId: string, points: number, reason: string): Observable<{ points: number }> {
    return this.data(this.http.post<ApiResponse<{ points: number }>>(this.url(`/room/add-points/${roomId}/${userId}`), { points, reason }));
  }

  allTrackers(): Observable<LabTracker[]> {
    return this.data(this.http.get<ApiResponse<unknown[]>>(this.url('/lab-tracker/overview')))
      .pipe(map(trackers => (trackers ?? []).map(tracker => new LabTracker(tracker))));
  }

  /** Challenges that use a lab, limited by the API to what the caller may see. */
  challengesForLab(labId: string): Observable<LabChallenge[]> {
    return this.data(this.http.get<ApiResponse<LabChallenge[]>>(this.url(`/ctf-entity/by-lab/${labId}`)))
      .pipe(map(challenges => challenges ?? []));
  }

  tracker(trackerId: string): Observable<LabTracker> {
    return this.data(this.http.get<ApiResponse<unknown>>(this.url(`/lab-tracker/${trackerId}`)))
      .pipe(map(tracker => new LabTracker(tracker)));
  }

  /** The copy's VM login, password included: fetched only when someone asks to see or copy it. */
  access(trackerId: string): Observable<LabAccess> {
    return this.data(this.http.get<ApiResponse<LabAccess>>(this.url(`/lab-tracker/${trackerId}/access`)));
  }

  refreshTracker(trackerId: string): Observable<LabTracker> {
    return this.data(this.http.get<ApiResponse<unknown>>(this.url(`/lab-tracker/refresh/${trackerId}`)))
      .pipe(map(tracker => new LabTracker(tracker)));
  }

  restartService(trackerId: string, service: string): Observable<LabTracker> {
    return this.data(this.http.post<ApiResponse<unknown>>(this.url(`/lab-tracker/${trackerId}/restart/${encodeURIComponent(service)}`), {}))
      .pipe(map(tracker => new LabTracker(tracker)));
  }

  logs(trackerId: string, serverId: string, service = 'all'): Observable<string> {
    return this.data(this.http.get<ApiResponse<string>>(this.url(`/lab-tracker/logs/${trackerId}/${serverId}/${encodeURIComponent(service)}`)));
  }

  labFiles(trackerId: string, serverId: string): Observable<LabFiles> {
    return this.data(this.http.get<ApiResponse<LabFiles>>(this.url(`/lab-tracker/settings/${trackerId}/${serverId}`)));
  }

  uploadLabFile(trackerId: string, serverId: string, change: { serviceName: string; index: number; targetPath: string; directory: boolean }, file: File): Observable<unknown> {
    const form = new FormData();
    form.append('volumeChanges', JSON.stringify([{ ...change, fileName: file.name }]));
    form.append('files', file, file.name);
    return this.data(this.http.post<ApiResponse<unknown>>(this.url(`/lab-tracker/settings/${trackerId}/${serverId}`), form));
  }

  servers(type: ServerType): Observable<RemoteServer[]> {
    const params = new HttpParams().set('serverType', type);
    return this.data(this.http.get<ApiResponse<unknown[]>>(this.url('/remote-server'), { params }))
      .pipe(map(servers => (servers ?? []).map(server => new RemoteServer(server))));
  }

  teams(): Observable<Team[]> {
    return this.data(this.http.get<ApiResponse<unknown[]>>(this.url('/team')))
      .pipe(map(teams => (teams ?? []).map(team => new Team(team))));
  }
}
