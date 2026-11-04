import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { EnvironmentService } from './environment.service';
import { LoginResponse } from '../models/dto/login-response.model';

// The refresh cookie must be sent and stored even when the API is on another origin (ng serve)
const withCookie = { withCredentials: true };

@Injectable({
  providedIn: 'root'
})
export class LoginService {

  constructor(
    private environmentService: EnvironmentService,
    private http: HttpClient
  ) { }

  private url(path: string): string {
    return `${this.environmentService.settings?.restUrl}/auth/${path}`;
  }

  login(username: string, password: string): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(this.url('login'), { username, password }, withCookie);
  }

  register(username: string, password: string): Observable<ApiResponse<boolean>> {
    return this.http.post<ApiResponse<boolean>>(this.url('register'), { username, password });
  }

  refreshToken(): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(this.url('refresh'), null, withCookie);
  }

  validateRefreshToken(): Observable<ApiResponse<boolean>> {
    return this.http.post<ApiResponse<boolean>>(this.url('refresh/validate'), null, withCookie);
  }

  logout(): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(this.url('logout'), null, withCookie);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(this.url('password'), { currentPassword, newPassword },
      withCookie);
  }
}
