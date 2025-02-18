import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Router } from '@angular/router';
import { EnvironmentService } from './environment.service';

@Injectable({
    providedIn: 'root'
})
export class LoginService {
    private readonly loginUrl: string;
    private readonly tokenUrl: string;

    constructor(
        private environmentService: EnvironmentService,
        private httpClient: HttpClient,
        private router: Router) {
        this.loginUrl = `${this.environmentService.settings?.restUrl}/api/auth/login`;
        this.tokenUrl = `${this.environmentService.settings?.restUrl}/api/auth/token`;
    }

    public login(username: string, password: string): Observable<any> {
        const credentials = { username: username, password: password };
        return this.httpClient.post<any>(this.loginUrl, credentials, {
            headers: new HttpHeaders(
                {
                    'Content-Type': 'application/json'
                }
            )
        });
    }

    public loginWithToken(token: string): Observable<any> {
        return this.httpClient.post<any>(this.tokenUrl, token, {
            headers: new HttpHeaders(
                {
                    'Content-Type': 'application/json',
                    'skip': "true"
                }
            )
        });
    }
    public logout(username: string): Observable<any> {
        console.log('logout....')
        return this.httpClient.delete<any>(`${this.tokenUrl}/logout/${username}`);
    }

    public checkToken(token: string): Observable<any> {
        return this.httpClient.post<any>(`${this.tokenUrl}/check`, token, {
            headers: new HttpHeaders(
                {
                    'Content-Type': 'application/json'
                }
            )
        });
    }

    public register(username: string, password: string, email: string): Observable<any> {
        const credentials = { username: username, password: password, email: email };
        return this.httpClient.post<any>(`${this.environmentService.settings?.restUrl}/auth/register`, credentials, {
            headers: new HttpHeaders(
                {
                    'Content-Type': 'application/json'
                }
            ),
            responseType: 'text' as 'json'
        });
    }
}
