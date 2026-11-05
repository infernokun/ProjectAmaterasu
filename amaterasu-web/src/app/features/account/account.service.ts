import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { LoginResponse } from '../../models/dto/login-response.model';
import { User } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { EnvironmentService } from '../../services/environment.service';

export interface AccountSession {
  id: string;
  userAgent: string | null;
  ipAddress: string | null;
  signedInAt: string | null;
  lastActive: string | null;
  expiresAt: string | null;
  current: boolean;
}

/**
 * The caller's own password and sessions. Calls that end sessions return a fresh access token for
 * this browser, which replaces the one in memory.
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);
  private readonly auth = inject(AuthService);

  private url(path: string): string {
    return `${this.env.settings?.restUrl}/auth${path}`;
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http
      .post<ApiResponse<LoginResponse>>(this.url('/password'), { currentPassword, newPassword }, { withCredentials: true })
      .pipe(tap(response => this.keep(response)), map(() => undefined));
  }

  sessions(): Observable<AccountSession[]> {
    return this.http
      .get<ApiResponse<AccountSession[]>>(this.url('/sessions'), { withCredentials: true })
      .pipe(map(response => response.data ?? []));
  }

  signOut(sessionId: string): Observable<void> {
    return this.http
      .delete<ApiResponse<LoginResponse>>(this.url(`/sessions/${encodeURIComponent(sessionId)}`), { withCredentials: true })
      .pipe(tap(response => this.keep(response)), map(() => undefined));
  }

  signOutOthers(): Observable<void> {
    return this.http
      .post<ApiResponse<LoginResponse>>(this.url('/sessions/sign-out-others'), {}, { withCredentials: true })
      .pipe(tap(response => this.keep(response)), map(() => undefined));
  }

  private keep(response: ApiResponse<LoginResponse>): void {
    const data = response?.data;
    if (data?.accessToken && data.user) {
      this.auth.setPayload(new User(data.user), data.accessToken);
    }
  }
}
