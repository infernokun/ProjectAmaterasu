import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../../../models/api-response.model';
import { EnvironmentService } from '../../../../services/environment.service';
import {
  Accepted, ActivityItem, Credited, LabTeamsView, ManageOverview, ManagePlayers, PlayerDetail, QuestionStats, ResetResult,
  SubmissionPage, SubmissionQuery, TeamCandidate, TeamMode,
} from './lab-manage.models';

/** HTTP for a lab's teams and its management. Admins and the lab's facilitators only; the API checks. */
@Injectable({ providedIn: 'root' })
export class LabManageApi {
  private readonly http = inject(HttpClient);
  private readonly environment = inject(EnvironmentService);

  private url(labId: string, path: string): string {
    return `${this.environment.settings?.restUrl}/room/${labId}${path}`;
  }

  private data<T>(request: Observable<ApiResponse<T>>): Observable<T> {
    return request.pipe(map(response => response.data));
  }

  /** The reply's message too, for actions whose toast says what happened. */
  private withMessage<T>(request: Observable<ApiResponse<T>>): Observable<{ data: T; message: string }> {
    return request.pipe(map(response => ({ data: response.data, message: response.message })));
  }

  teams(labId: string): Observable<LabTeamsView> {
    return this.data(this.http.get<ApiResponse<LabTeamsView>>(this.url(labId, '/teams')));
  }

  candidates(labId: string): Observable<TeamCandidate[]> {
    return this.data(this.http.get<ApiResponse<TeamCandidate[]>>(this.url(labId, '/teams/candidates')))
      .pipe(map(people => people ?? []));
  }

  setTeamMode(labId: string, teamMode: TeamMode): Observable<LabTeamsView> {
    return this.data(this.http.put<ApiResponse<LabTeamsView>>(this.url(labId, '/teams/mode'), { teamMode }));
  }

  createTeam(labId: string, name: string, description?: string): Observable<LabTeamsView> {
    return this.data(this.http.post<ApiResponse<LabTeamsView>>(this.url(labId, '/teams'), { name, description }));
  }

  editTeam(labId: string, teamId: string, name: string, description?: string): Observable<LabTeamsView> {
    return this.data(this.http.put<ApiResponse<LabTeamsView>>(this.url(labId, `/teams/${teamId}`), { name, description }));
  }

  deleteTeam(labId: string, teamId: string): Observable<LabTeamsView> {
    return this.data(this.http.delete<ApiResponse<LabTeamsView>>(this.url(labId, `/teams/${teamId}`)));
  }

  addMembers(labId: string, teamId: string, userIds: string[]): Observable<LabTeamsView> {
    return this.data(this.http.post<ApiResponse<LabTeamsView>>(this.url(labId, `/teams/${teamId}/members`), { userIds }));
  }

  removeMember(labId: string, teamId: string, userId: string): Observable<LabTeamsView> {
    return this.data(this.http.delete<ApiResponse<LabTeamsView>>(this.url(labId, `/teams/${teamId}/members/${userId}`)));
  }

  copySiteTeams(labId: string): Observable<{ data: LabTeamsView; message: string }> {
    return this.withMessage(this.http.post<ApiResponse<LabTeamsView>>(this.url(labId, '/teams/copy-site'), {}));
  }

  overview(labId: string): Observable<ManageOverview> {
    return this.data(this.http.get<ApiResponse<ManageOverview>>(this.url(labId, '/manage/overview')));
  }

  submissions(labId: string, query: SubmissionQuery = {}): Observable<SubmissionPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '' && value !== 'all') params = params.set(key, String(value));
    }
    return this.data(this.http.get<ApiResponse<SubmissionPage>>(this.url(labId, '/manage/submissions'), { params }));
  }

  players(labId: string): Observable<ManagePlayers> {
    return this.data(this.http.get<ApiResponse<ManagePlayers>>(this.url(labId, '/manage/players')));
  }

  player(labId: string, userId: string): Observable<PlayerDetail> {
    return this.data(this.http.get<ApiResponse<PlayerDetail>>(this.url(labId, `/manage/players/${userId}`)));
  }

  questions(labId: string): Observable<QuestionStats[]> {
    return this.data(this.http.get<ApiResponse<QuestionStats[]>>(this.url(labId, '/manage/questions')))
      .pipe(map(questions => questions ?? []));
  }

  activity(labId: string, limit = 200): Observable<ActivityItem[]> {
    const params = new HttpParams().set('limit', limit);
    return this.data(this.http.get<ApiResponse<ActivityItem[]>>(this.url(labId, '/manage/activity'), { params }))
      .pipe(map(items => items ?? []));
  }

  /** Credit for a submission, or for a player and a question; points default to the question's. */
  credit(labId: string, body: { submissionId?: string; userId?: string; questionId?: string; points?: number | null; reason: string }):
    Observable<{ data: Credited; message: string }> {
    return this.withMessage(this.http.post<ApiResponse<Credited>>(this.url(labId, '/manage/credit'), body));
  }

  acceptAnswer(labId: string, body: {
    submissionId?: string; questionId?: string; answer?: string; caseSensitive: boolean; creditExisting: boolean; reason: string;
  }): Observable<{ data: Accepted; message: string }> {
    return this.withMessage(this.http.post<ApiResponse<Accepted>>(this.url(labId, '/manage/accept-answer'), body));
  }

  /** Points for a player or for a team as a whole. */
  award(labId: string, body: { userId?: string; teamId?: string; points: number; reason: string }):
    Observable<{ data: unknown; message: string }> {
    return this.withMessage(this.http.post<ApiResponse<unknown>>(this.url(labId, '/manage/award'), body));
  }

  reset(labId: string, body: { userId: string; questionId?: string | null; reason: string }): Observable<{ data: ResetResult; message: string }> {
    return this.withMessage(this.http.post<ApiResponse<ResetResult>>(this.url(labId, '/manage/reset'), { ...body, confirm: true }));
  }
}
