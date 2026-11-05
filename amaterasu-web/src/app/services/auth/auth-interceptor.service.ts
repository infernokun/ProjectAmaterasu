import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, switchMap, filter, take } from 'rxjs/operators';
import { AuthService } from '../auth.service';
import { EnvironmentService } from '../environment.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private refreshTokenSubject: BehaviorSubject<any> = new BehaviorSubject<any>(null);

  constructor(private authService: AuthService,
    private environmentService: EnvironmentService
  ) { }


  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    console.log('Interceptor called for URL:', req.url);

    // CRITICAL: Skip if environment settings not loaded yet
    if (!this.environmentService.settings?.restUrl) {
      console.warn('🔍 Environment not loaded yet, proceeding without auth for:', req.url);
      return next.handle(req);
    }

    // Skip adding auth header for auth endpoints
    if (this.isAuthEndpoint(req.url)) {
      console.log('🔍 Skipping auth endpoint:', req.url);
      return next.handle(req);
    }

    const accessToken = this.authService.getAccessToken();

    if (!accessToken) {
      console.warn('🔒 No access token found, skipping request:', req.url);
      return next.handle(req);
    }

    // Check if token needs refresh
    const decodedToken = this.authService.decodeToken(accessToken);
    const isCloseToExpiry = this.authService.isTokenCloseToExpiry(decodedToken);

    if (isCloseToExpiry && !this.isRefreshing) {
      console.log('🔒 Access token is expired or near expiration. Attempting refresh...');
      return this.handleTokenRefreshAndRetry(req, next);
    }

    // Add access token to request
    req = this.addTokenHeader(req, accessToken);
    console.log('🔍 Added token to request header');

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        console.log('🔍 Caught error in interceptor:', error.status, error.message);

        // Only handle 401s if environment is loaded
        if (error.status === 401 && !this.isAuthEndpoint(req.url) && this.environmentService.settings?.restUrl) {
          console.log('🔒 Received 401 (fallback). Handling via catchError...');
          return this.handleTokenRefreshAndRetry(req, next);
        }
        return throwError(() => error);
      })
    );
  }

  private addTokenHeader(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      headers: request.headers.set('Authorization', `Bearer ${token}`)
    });
  }

  // Signing in and refreshing use the cookie; the rest of /api/auth (password, sessions) needs the token
  private isAuthEndpoint(url: string): boolean {
    return /\/api\/auth\/(login|register|refresh|logout)/.test(url) || url.includes('/api/application-info');
  }

  private handleTokenRefreshAndRetry(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    console.log('🔄 Handling token refresh for retry of:', request.url);

    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null); // Indicate refresh is in progress, no token available yet

      console.log('🔄 Starting token refresh...');

      return this.authService.forceTokenRefresh().pipe(
        switchMap((success: boolean) => {
          console.log('🔄 Token refresh result:', success);
          this.isRefreshing = false; // Refresh is complete (either success or failure)

          if (success) {
            const newAccessToken = this.authService.getAccessToken();
            console.log('🔄 New access token available:', !!newAccessToken);
            if (newAccessToken) {
              this.refreshTokenSubject.next(newAccessToken); // Emit the new token for queued requests
              console.log('🔄 Retrying original request with new token for:', request.url);
              // Retry the original request with the newly obtained token
              return next.handle(this.addTokenHeader(request, newAccessToken));
            } else {
              // Should not happen if refresh was successful, but handle defensively
              console.error('🔄 Refresh reported success but no new access token found.');
              this.refreshTokenSubject.next('');
              this.authService.logout();
              return throwError(() => new Error('Token refresh failed (no new token)'));
            }
          } else {
            // Refresh failed, logout
            console.log('🔄 Refresh failed, logging out');
            this.refreshTokenSubject.next('');
            this.authService.logout();
            return throwError(() => new Error('Token refresh failed'));
          }
        }),
        catchError((error) => {
          console.log('🔄 Token refresh error during refresh attempt:', error);
          this.isRefreshing = false;
          this.refreshTokenSubject.next('');
          this.authService.logout();
          // Propagate the original refresh error
          return throwError(() => error);
        })
      );
    } else {
      console.log('⏳ Refresh in progress, queuing request for retry:', request.url);
      // If a refresh is already in progress, wait for it to complete
      return this.refreshTokenSubject.pipe(
        filter(token => token !== null), // Wait until the refresh finishes; '' means it failed
        take(1),
        switchMap((token) => {
          if (!token) {
            return throwError(() => new Error('Token refresh failed'));
          }
          console.log('⏳ Retrying queued request with new token for:', request.url, 'Token available:', !!token);
          // Retry the original request with the newly available token
          return next.handle(this.addTokenHeader(request, token));
        })
      );
    }
  }
}
