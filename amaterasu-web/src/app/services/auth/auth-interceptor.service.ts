import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, switchMap, filter, take } from 'rxjs/operators';
import { AuthService } from '../auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private refreshTokenSubject: BehaviorSubject<any> = new BehaviorSubject<any>(null);

  constructor(private authService: AuthService) { }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Skip adding auth header for auth endpoints
    if (this.isAuthEndpoint(req.url)) {
      return next.handle(req);
    }

    // Add access token to request
    const accessToken = this.authService.getAccessToken();
    if (accessToken) {
      req = this.addTokenHeader(req, accessToken);
    }

    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        // Handle 401 errors by attempting token refresh
        if (error.status === 401 && !this.isAuthEndpoint(req.url)) {
          return this.handle401Error(req, next);
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

  private isAuthEndpoint(url: string): boolean {
    return url.includes('/api/auth/') || url.includes('/api/application-info');
  }

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    console.log('🔄 Handling 401 error for:', request.url);
    console.log('🔄 isRefreshing:', this.isRefreshing);

    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      console.log('🔄 Starting token refresh...');

      return this.authService.forceTokenRefresh().pipe(
        switchMap((success: boolean) => {
          console.log('🔄 Token refresh result:', success);
          this.isRefreshing = false;

          if (success) {
            const newAccessToken = this.authService.getAccessToken();
            console.log('🔄 New access token available:', !!newAccessToken);
            this.refreshTokenSubject.next(newAccessToken);

            if (newAccessToken) {
              console.log('🔄 Retrying original request with new token for:', request.url);
              return next.handle(this.addTokenHeader(request, newAccessToken));
            }
          }

          console.log('🔄 Refresh failed, logging out');
          // Refresh failed, redirect to login
          this.authService.logout();
          return throwError(() => new Error('Token refresh failed'));
        }),
        catchError((error) => {
          console.log('🔄 Token refresh error:', error);
          this.isRefreshing = false;
          this.authService.logout();
          return throwError(() => error);
        })
      );
    }

    console.log('⏳ Refresh in progress, queuing request for:', request.url);
    // If refresh is in progress, wait for it to complete
    return this.refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap((token) => {
        console.log('⏳ Retrying queued request with token for:', request.url, 'Token available:', !!token);
        return next.handle(this.addTokenHeader(request, token));
      })
    );
  }
}
