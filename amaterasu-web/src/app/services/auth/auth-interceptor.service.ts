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
    // CRITICAL: Skip if environment settings not loaded yet
    if (!this.environmentService.settings?.restUrl) {
      return next.handle(req);
    }

    // Skip adding auth header for auth endpoints
    if (this.isAuthEndpoint(req.url)) {
      return next.handle(req);
    }

    const accessToken = this.authService.getAccessToken();

    if (!accessToken) {
      return next.handle(req);
    }

    // Check if token needs refresh
    const decodedToken = this.authService.decodeToken(accessToken);
    const isCloseToExpiry = this.authService.isTokenCloseToExpiry(decodedToken);

    if (isCloseToExpiry && !this.isRefreshing) {
      return this.handleTokenRefreshAndRetry(req, next);
    }

    // Add access token to request
    req = this.addTokenHeader(req, accessToken);

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        // Only handle 401s if environment is loaded
        if (error.status === 401 && !this.isAuthEndpoint(req.url) && this.environmentService.settings?.restUrl) {
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

  // Signing in and refreshing use the cookie; the rest of /api/auth (password, sessions) needs the token.
  // Reading the app info and first-run setup are public; changing a setting under it needs the token.
  private isAuthEndpoint(url: string): boolean {
    return /\/api\/auth\/(login|register|refresh|logout)/.test(url) || /\/api\/application-info(\?|$)/.test(url);
  }

  private handleTokenRefreshAndRetry(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null); // Indicate refresh is in progress, no token available yet


      return this.authService.forceTokenRefresh().pipe(
        switchMap((success: boolean) => {
          this.isRefreshing = false; // Refresh is complete (either success or failure)

          if (success) {
            const newAccessToken = this.authService.getAccessToken();
            if (newAccessToken) {
              this.refreshTokenSubject.next(newAccessToken); // Emit the new token for queued requests
              // Retry the original request with the newly obtained token
              return next.handle(this.addTokenHeader(request, newAccessToken));
            } else {
              // Should not happen if refresh was successful, but handle defensively
              this.refreshTokenSubject.next('');
              this.authService.logout();
              return throwError(() => new Error('Token refresh failed (no new token)'));
            }
          } else {
            // Refresh failed, logout
            this.refreshTokenSubject.next('');
            this.authService.logout();
            return throwError(() => new Error('Token refresh failed'));
          }
        }),
        catchError((error) => {
          this.isRefreshing = false;
          this.refreshTokenSubject.next('');
          this.authService.logout();
          // Propagate the original refresh error
          return throwError(() => error);
        })
      );
    } else {
      // If a refresh is already in progress, wait for it to complete
      return this.refreshTokenSubject.pipe(
        filter(token => token !== null), // Wait until the refresh finishes; '' means it failed
        take(1),
        switchMap((token) => {
          if (!token) {
            return throwError(() => new Error('Token refresh failed'));
          }
          // Retry the original request with the newly available token
          return next.handle(this.addTokenHeader(request, token));
        })
      );
    }
  }
}
