import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { AppInfoStore } from '../core/stores/app-info.store';
import { Role } from '../enums/role.enum';
import { rolesFromToken } from '../core/auth/roles';

export const SIGN_IN_PATH = '/welcome';
const REDIRECT_KEY = 'redirectUrl';

function rememberUrl(url: string): void {
  try {
    sessionStorage.setItem(REDIRECT_KEY, url);
  } catch {
    // Without storage the user lands on Home after signing in
  }
}

/** Where to go after signing in: the page that sent the user to sign in, or Home. */
export function takeRedirectUrl(): string {
  try {
    const url = sessionStorage.getItem(REDIRECT_KEY);
    sessionStorage.removeItem(REDIRECT_KEY);
    return url && url.startsWith('/') && !url.startsWith(SIGN_IN_PATH) ? url : '/home';
  } catch {
    return '/home';
  }
}

function signInFor(router: Router, url: string): UrlTree {
  rememberUrl(url);
  return router.parseUrl(SIGN_IN_PATH);
}

function checkSession(auth: AuthService): Observable<boolean> {
  return auth.isAuthenticated().pipe(catchError(() => of(false)));
}

/** Signed-in users only; everyone else goes to the landing page. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return checkSession(auth).pipe(map(signedIn => signedIn || signInFor(router, state.url)));
};

/** The landing page; signed-in users go straight to Home. */
export const signedOutGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return checkSession(auth).pipe(map(signedIn => (signedIn ? router.parseUrl('/home') : true)));
};

/**
 * Users with any of `roles` (from the access token, which the API checks too). Others go to Home,
 * so nobody reaches a page whose actions would be refused.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return checkSession(auth).pipe(
      map(signedIn => {
        if (!signedIn) {
          return signInFor(router, state.url);
        }
        const held = rolesFromToken(auth.decodeToken(auth.getAccessToken() ?? ''));
        return held.some(role => roles.includes(role)) || router.parseUrl('/home');
      }),
    );
  };
}

export const adminGuard: CanActivateFn = roleGuard(Role.ADMIN);

/** Developer pages, only when the environment turns them on. */
export const devToolsGuard: CanActivateFn = () =>
  inject(AppInfoStore).devTools() || inject(Router).parseUrl('/home');
