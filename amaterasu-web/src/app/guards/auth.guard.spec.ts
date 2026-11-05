import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { Role } from '../enums/role.enum';
import { AuthService } from '../services/auth.service';
import { FakeAuthService } from '../testing/fake-auth';
import { authGuard, roleGuard, signedOutGuard, takeRedirectUrl } from './auth.guard';

describe('auth guards', () => {
  let auth: FakeAuthService;

  beforeEach(() => {
    auth = new FakeAuthService();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    sessionStorage.removeItem('redirectUrl');
  });

  const run = (guard: unknown, url = '/run/live') =>
    firstValueFrom(TestBed.runInInjectionContext(() =>
      (guard as (r: ActivatedRouteSnapshot, s: RouterStateSnapshot) => Observable<boolean | UrlTree>)(
        {} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot)));

  const path = (result: boolean | UrlTree) => (result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result);

  it('sends signed-out visitors to the landing page and remembers where they were going', async () => {
    expect(path(await run(authGuard, '/rooms/r1'))).toBe('/welcome');
    expect(takeRedirectUrl()).toBe('/rooms/r1');
    expect(takeRedirectUrl()).toBe('/home');
  });

  it('lets signed-in users through', async () => {
    auth.signIn({ id: 'u1', username: 'jmorales' }, 'MEMBER');
    expect(await run(authGuard)).toBeTrue();
  });

  it('accepts any of several roles', async () => {
    const guard = roleGuard(Role.FACILITATOR, Role.ADMIN);
    auth.signIn({ id: 'u1' }, 'FACILITATOR');
    expect(await run(guard)).toBeTrue();
    auth.signIn({ id: 'u1' }, 'ADMIN');
    expect(await run(guard)).toBeTrue();
  });

  it('sends members away from pages they cannot use', async () => {
    auth.signIn({ id: 'u1' }, 'MEMBER');
    expect(path(await run(roleGuard(Role.CREATOR, Role.ADMIN)))).toBe('/home');
  });

  it('sends signed-in users from the landing page to Home', async () => {
    auth.signIn({ id: 'u1' }, 'MEMBER');
    expect(path(await run(signedOutGuard))).toBe('/home');
  });
});
