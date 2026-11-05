import { BehaviorSubject, of } from 'rxjs';
import { User } from '../models/user.model';
import { UserPayload } from '../services/auth.service';

/** A stand-in AuthService whose session tests can set. */
export class FakeAuthService {
  readonly payload$ = new BehaviorSubject<UserPayload | undefined>(undefined);
  readonly user$ = new BehaviorSubject<User | undefined>(undefined);
  readonly loading$ = of(false);
  signedIn = false;
  logout = jasmine.createSpy('logout');
  setUser = jasmine.createSpy('setUser');

  signIn(user: Partial<User> & { id: string }, roles: string): void {
    const accessToken = tokenWithRoles(roles);
    const full = new User(user);
    this.signedIn = true;
    this.payload$.next({ user: full, accessToken });
    this.user$.next(full);
  }

  signOutNow(): void {
    this.signedIn = false;
    this.payload$.next(undefined);
    this.user$.next(undefined);
  }

  isAuthenticated() {
    return of(this.signedIn);
  }

  getAccessToken(): string | null {
    return this.payload$.value?.accessToken ?? null;
  }

  decodeToken(token: string): unknown {
    try {
      return JSON.parse(atob(token.split('.')[1]));
    } catch {
      return null;
    }
  }
}

export function tokenWithRoles(roles: string): string {
  const body = btoa(JSON.stringify({ sub: 'u1', roles, exp: Math.floor(Date.now() / 1000) + 600 }));
  return `e30.${body}.sig`;
}
