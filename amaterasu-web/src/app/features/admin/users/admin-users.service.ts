import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Role } from '../../../enums/role.enum';
import { LabStatus } from '../../../enums/lab-status.enum';
import { ApiResponse } from '../../../models/api-response.model';
import { EnvironmentService } from '../../../services/environment.service';

export interface TeamRef {
  id: string;
  name: string;
  description?: string | null;
}

export interface AdminUser {
  id: string;
  username: string;
  role: Role;
  disabled: boolean;
  team?: TeamRef | null;
  lastSignIn?: string | null;
  createdAt?: string | null;
}

export interface AdminTeam extends TeamRef {
  teamActiveLabs?: string[];
}

/** Just what the team cards need from a lab tracker. */
export interface TeamLab {
  id: string;
  labStatus: LabStatus;
  teamId: string | null;
  labName: string;
}

export interface ImportedRow {
  row: number;
  username: string;
  password: string;
  role: Role;
  team: string | null;
}

export interface ImportResult {
  created: { row: number; id: string; username: string; role: Role; team: string | null }[];
  skipped: { row: number; username: string; reason: string }[];
}

export interface NewUser {
  username: string;
  password: string;
  role: Role;
  teamId: string | null;
}

/** Admin endpoints for accounts and teams. */
@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  private url(path: string): string {
    return `${this.env.settings?.restUrl}${path}`;
  }

  users(): Observable<AdminUser[]> {
    return this.http.get<ApiResponse<AdminUser[]>>(this.url('/user')).pipe(map(r => (r.data ?? []).map(toUser)));
  }

  teams(): Observable<AdminTeam[]> {
    return this.http.get<ApiResponse<AdminTeam[]>>(this.url('/team')).pipe(map(r => r.data ?? []));
  }

  /** Every team's labs; admins get all trackers. */
  teamLabs(): Observable<TeamLab[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/lab-tracker')).pipe(
      map(r => (r.data ?? []).map(tracker => ({
        id: tracker.id,
        labStatus: tracker.labStatus,
        teamId: tracker.labOwner?.id ?? null,
        labName: tracker.labStarted?.name ?? 'Lab',
      }))),
    );
  }

  createUser(user: NewUser): Observable<AdminUser> {
    return this.http.post<ApiResponse<AdminUser>>(this.url('/user'), user).pipe(map(r => toUser(r.data)));
  }

  changeRole(userId: string, role: Role): Observable<AdminUser> {
    return this.http.put<ApiResponse<AdminUser>>(this.url(`/user/${userId}/role`), { role }).pipe(map(r => toUser(r.data)));
  }

  setTeam(userId: string, teamId: string | null): Observable<AdminUser> {
    const params: Record<string, string> = teamId ? { userId, teamId } : { userId };
    return this.http.put<ApiResponse<AdminUser>>(this.url('/user/team'), {}, { params }).pipe(map(r => toUser(r.data)));
  }

  /** Moves every listed user to the team, or off their team; all or none. */
  setTeamMany(userIds: string[], teamId: string | null): Observable<AdminUser[]> {
    return this.http.put<ApiResponse<any[]>>(this.url('/user/team/bulk'), { userIds, teamId })
      .pipe(map(r => (r.data ?? []).map(toUser)));
  }

  importUsers(users: ImportedRow[]): Observable<ImportResult> {
    return this.http.post<ApiResponse<ImportResult>>(this.url('/user/import'), { users })
      .pipe(map(r => r.data ?? { created: [], skipped: [] }));
  }

  setDisabled(userId: string, disabled: boolean): Observable<AdminUser> {
    return this.http.put<ApiResponse<AdminUser>>(this.url(`/user/${userId}/disabled`), { disabled }).pipe(map(r => toUser(r.data)));
  }

  resetPassword(userId: string, password: string): Observable<AdminUser> {
    return this.http.put<ApiResponse<AdminUser>>(this.url(`/user/${userId}/password`), { password }).pipe(map(r => toUser(r.data)));
  }

  createTeam(name: string, description: string): Observable<AdminTeam> {
    return this.http.post<ApiResponse<AdminTeam>>(this.url('/team'), { name, description }).pipe(map(r => r.data));
  }

  editTeam(teamId: string, name: string, description: string): Observable<AdminTeam> {
    return this.http.put<ApiResponse<AdminTeam>>(this.url(`/team/${teamId}`), { name, description }).pipe(map(r => r.data));
  }

  deleteTeam(teamId: string): Observable<unknown> {
    return this.http.delete(this.url(`/team/${teamId}`));
  }
}

function toUser(raw: any): AdminUser {
  return {
    id: raw.id,
    username: raw.username,
    role: raw.role,
    disabled: !!raw.disabled,
    team: raw.team ? { id: raw.team.id, name: raw.team.name, description: raw.team.description } : null,
    lastSignIn: raw.lastSignIn ?? null,
    createdAt: raw.createdAt ?? null,
  };
}
