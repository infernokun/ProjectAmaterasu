import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, take, filter, timeout } from 'rxjs/operators';
import { Router } from '@angular/router';
import { User } from '../models/user.model';
import { ApiResponse } from '../models/api-response.model';
import { LoginResponse } from '../models/dto/login-response.model';
import { LoginService } from './login.service';
import { UserService } from './user.service';

export interface UserPayload {
  user: User;
  accessToken: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService implements OnDestroy {
  private payloadSubject: BehaviorSubject<UserPayload | undefined> = new BehaviorSubject<UserPayload | undefined>(undefined);
  public payload$: Observable<UserPayload | undefined> = this.payloadSubject.asObservable();

  private userSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);
  public user$: Observable<User | undefined> = this.userSubject.asObservable();

  private loadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  public loading$: Observable<boolean> = this.loadingSubject.asObservable();

  // Add refresh state management
  private isRefreshing = false;
  private refreshTokenSubject: BehaviorSubject<string | null> = new BehaviorSubject<string | null>(null);

  // The access token is only kept in memory and the refresh token is an HttpOnly cookie, so a script
  // injected into the page can't read either. This flag only says a cookie refresh is worth trying.
  private accessToken: string | null = null;
  private readonly SESSION_HINT_KEY = 'amaterasu.signedIn';
  private readonly LEGACY_TOKEN_KEYS = ['accessToken', 'refreshToken'];
  private readonly TOKEN_REFRESH_BUFFER = 2 * 60 * 1000; // 2 minutes before expiry

  private lastActivityTime: number = Date.now();
  private refreshCheckInterval: any;
  private readonly ACTIVITY_TIMEOUT = 15 * 60 * 1000; // 15 minutes
  private readonly REFRESH_CHECK_INTERVAL = 60 * 1000; // Check every minute when active

  constructor(
    private loginService: LoginService,
    private router: Router,
    private userService: UserService
  ) {
    this.LEGACY_TOKEN_KEYS.forEach(key => localStorage.removeItem(key));
    this.startSmartTokenRefresh();
    this.trackUserActivity();
    this.followSignOutInOtherTabs();
  }

  private followSignOutInOtherTabs(): void {
    window.addEventListener('storage', (event) => {
      if (event.key === this.SESSION_HINT_KEY && !event.newValue && this.accessToken) {
        this.performLogout();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.refreshCheckInterval) {
      clearInterval(this.refreshCheckInterval);
    }
  }

  private startSmartTokenRefresh(): void {
    this.refreshCheckInterval = setInterval(() => {
      this.checkTokenRefreshNeeded();
    }, this.REFRESH_CHECK_INTERVAL);
  }

  private checkTokenRefreshNeeded(): void {
    // Only check if user has been active recently
    const timeSinceActivity = Date.now() - this.lastActivityTime;
    if (timeSinceActivity > this.ACTIVITY_TIMEOUT) {
      return;
    }

    // Only check if page is visible (not a background tab)
    if (document.hidden) {
      return;
    }

    const accessToken = this.accessToken;
    if (!accessToken) {
      return;
    }

    const decodedToken = this.decodeToken(accessToken);
    if (!decodedToken) {
      return;
    }

    // Only refresh if close to expiry AND user is active
    if (this.isTokenCloseToExpiry(decodedToken) && !this.isRefreshing) {
      // A failed refresh logs the user out; performTokenRefresh never errors
      this.performTokenRefresh().subscribe();
    }
  }

  private trackUserActivity(): void {
    // Track user activity to determine if they're actively using the app
    const activityEvents = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];

    const updateActivity = () => {
      this.lastActivityTime = Date.now();
    };

    // Add throttling to avoid excessive updates
    let throttleTimer: any;
    const throttledUpdate = () => {
      if (throttleTimer) return;
      throttleTimer = setTimeout(() => {
        updateActivity();
        throttleTimer = null;
      }, 30000); // Update at most once per 30 seconds
    };

    activityEvents.forEach(event => {
      document.addEventListener(event, throttledUpdate, true);
    });
  }

  isAuthenticated(): Observable<boolean> {
    const accessToken = this.accessToken;

    // After a reload the access token is gone; the refresh cookie restores the session
    if (!accessToken) {
      return this.hasSessionHint() ? this.performTokenRefresh() : of(false);
    }

    const decodedToken = this.decodeToken(accessToken);
    if (!decodedToken || !this.isTokenStructureValid(decodedToken)) {
      this.clearAuthState();
      return of(false);
    }

    if (this.isTokenExpired(decodedToken)) {
      return this.performTokenRefresh();
    }

    if (this.payloadSubject.value?.accessToken === accessToken) {
      return of(true);
    }
    return this.setupAuthFromValidToken(accessToken, decodedToken);
  }

  setPayload(user: User, accessToken: string): void {
    const payload: Readonly<UserPayload> = { user, accessToken };

    // Store first: subscribers start making requests as soon as the user is emitted
    this.accessToken = accessToken;
    localStorage.setItem(this.SESSION_HINT_KEY, '1');
    this.payloadSubject.next(payload);
    this.userSubject.next(user);
    this.setLoading(false);
  }

  setLoading(loading: boolean): void {
    this.loadingSubject.next(loading);
  }

  decodeToken(token: string): any {
    try {
      if (!token || token.split('.').length !== 3) {
        return null;
      }
      // JWTs use base64url, which atob does not accept as is
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=')));
    } catch (error) {
      return null;
    }
  }

  private isTokenStructureValid(decodedToken: any): boolean {
    return decodedToken &&
      typeof decodedToken.exp === 'number' &&
      typeof decodedToken.sub === 'string' &&
      decodedToken.exp > 0;
  }

  private isTokenExpired(decodedToken: any): boolean {
    return decodedToken.exp * 1000 <= Date.now();
  }

  isTokenCloseToExpiry(decodedToken: any): boolean {
    const currentTime = Date.now();
    const expiryTime = decodedToken.exp * 1000;
    const timeUntilExpiry = expiryTime - currentTime;

    return timeUntilExpiry <= this.TOKEN_REFRESH_BUFFER;
  }

  private setupAuthFromValidToken(accessToken: string, decodedToken: any): Observable<boolean> {
    // Always fetch full user object from server using ID in token
    return this.userService.getUserById(decodedToken.sub).pipe(
      map((user: User | undefined) => {
        if (!user) {
          this.clearAuthState();
          return false;
        }
        this.setPayload(user, accessToken);
        return true;
      }),
      catchError((error) => this.handleAuthError('Failed to fetch user', error))
    );
  }

  performTokenRefresh(): Observable<boolean> {
    if (!this.hasSessionHint()) {
      this.clearAuthState();
      return of(false);
    }

    if (this.isRefreshing) {
      return this.refreshTokenSubject.pipe(
        filter(newToken => newToken !== null),
        take(1),
        map(newToken => !!newToken)
      );
    }

    this.isRefreshing = true;
    this.refreshTokenSubject.next(null);
    this.setLoading(true);

    return this.loginService.refreshToken().pipe(
      map((response: ApiResponse<LoginResponse>) => {
        if (!response?.data?.accessToken) {
          throw new Error('Invalid refresh response');
        }

        const newAccessToken = response.data.accessToken;

        const decodedToken = this.decodeToken(newAccessToken);
        if (!decodedToken || !this.isTokenStructureValid(decodedToken)) {
          throw new Error('Invalid refreshed access token');
        }

        // Use the user data from the refresh response instead of making another API call
        const user = response.data.user;
        if (!user) {
          throw new Error('User data missing from refresh response');
        }

        this.setPayload(new User(user), newAccessToken);
        this.refreshTokenSubject.next(newAccessToken);
        this.isRefreshing = false;

        return true;
      }),
      catchError((error) => {
        this.isRefreshing = false;
        this.refreshTokenSubject.next(''); // releases callers waiting on this refresh
        return this.handleAuthError('Token refresh failed', error);
      })
    );
  }

  logout(): void {
    this.setLoading(true);

    if (this.hasSessionHint()) {
      // Revokes the session and clears the refresh cookie
      this.loginService.logout().pipe(
        timeout(5000),
        catchError((error) => {
          return of(null);
        })
      ).subscribe(() => {
        this.performLogout();
      });
    } else {
      this.performLogout();
    }
  }

  private performLogout(): void {
    this.clearAuthState();
    this.router.navigate(['/welcome']);
  }

  private clearAuthState(): void {
    this.accessToken = null;
    localStorage.removeItem(this.SESSION_HINT_KEY);
    this.payloadSubject.next(undefined);
    this.userSubject.next(undefined);
    this.setLoading(false);
    this.isRefreshing = false;
    this.refreshTokenSubject.next(null);
  }

  private handleAuthError(message: string, error?: any): Observable<boolean> {
    this.clearAuthState();
    return of(false);
  }

  setUser(user: User): void {
    const currentUser = this.userSubject.value;
    if (currentUser?.id === user.id) {
      this.userSubject.next(user);

      // Update payload with new user data while keeping the same tokens
      const currentPayload = this.payloadSubject.value;
      if (currentPayload) {
        this.payloadSubject.next({
          ...currentPayload,
          user: user
        });
      }
    }
  }

  getUser(): User | undefined {
    return this.userSubject.value;
  }

  private hasSessionHint(): boolean {
    return localStorage.getItem(this.SESSION_HINT_KEY) === '1';
  }

  // Public method to get current access token (for HTTP interceptors)
  public getAccessToken(): string | null {
    return this.accessToken;
  }

  // Public method to manually refresh token
  public forceTokenRefresh(): Observable<boolean> {
    return this.performTokenRefresh();
  }

  // Check if user has specific role
  public hasRole(role: string): Observable<boolean> {
    return this.payload$.pipe(
      map(payload => {
        if (!payload?.accessToken) return false;
        const decodedToken = this.decodeToken(payload.accessToken);
        const roles = decodedToken?.roles || '';
        return roles.split(' ').includes(role);
      })
    );
  }
}
