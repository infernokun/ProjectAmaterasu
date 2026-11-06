import { TestBed } from '@angular/core/testing';
import { HttpHandler, HttpRequest, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import { AuthService } from '../auth.service';
import { EnvironmentService } from '../environment.service';
import { AuthInterceptor } from './auth-interceptor.service';

describe('AuthInterceptorService', () => {
  let service: AuthInterceptor;
  const auth = {
    getAccessToken: () => 'token-1',
    decodeToken: () => ({}),
    isTokenCloseToExpiry: () => false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthInterceptor,
        { provide: AuthService, useValue: auth },
        { provide: EnvironmentService, useValue: { settings: { restUrl: '/api' } } },
      ]
    });
    service = TestBed.inject(AuthInterceptor);
  });

  function headerFor(method: string, url: string): string | null {
    let sent: HttpRequest<unknown> | undefined;
    const next: HttpHandler = { handle: req => { sent = req; return of(new HttpResponse()); } };
    service.intercept(new HttpRequest(method, url, null), next).subscribe();
    return sent!.headers.get('Authorization');
  }

  it('leaves the token off sign in and the public app info, and sends it everywhere else', () => {
    expect(headerFor('POST', '/api/auth/login')).toBeNull();
    expect(headerFor('GET', '/api/application-info')).toBeNull();
    expect(headerFor('POST', '/api/application-info')).toBeNull();
    expect(headerFor('PUT', '/api/application-info/classification')).toBe('Bearer token-1');
    expect(headerFor('GET', '/api/labs')).toBe('Bearer token-1');
  });
});
