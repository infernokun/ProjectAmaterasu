import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AccountService, AccountSession } from './account.service';
import { AccountStore } from './account.store';

const session = (id: string, current = false): AccountSession => ({
  id, userAgent: 'Mozilla/5.0 (Macintosh) Chrome/130.0', ipAddress: '10.88.4.21', signedInAt: '2026-11-04T08:58:00Z',
  lastActive: '2026-11-04T09:30:00Z', expiresAt: null, current,
});

describe('AccountStore', () => {
  let api: jasmine.SpyObj<AccountService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<AccountService>('AccountService', ['sessions', 'signOut', 'signOutOthers', 'changePassword']);
    api.sessions.and.returnValue(of([session('here', true), session('phone'), session('laptop')]));
    TestBed.configureTestingModule({ providers: [AccountStore, { provide: AccountService, useValue: api }] });
  });

  it('splits this browser from the other sessions', () => {
    const store = TestBed.inject(AccountStore);
    store.loadSessions();
    expect(store.current()?.id).toBe('here');
    expect(store.others().map(s => s.id)).toEqual(['phone', 'laptop']);
  });

  it('signs out one session or every other one', async () => {
    const store = TestBed.inject(AccountStore);
    store.loadSessions();
    api.signOut.and.returnValue(of(undefined));
    await store.signOut('phone');
    expect(api.signOut).toHaveBeenCalledWith('phone');
    expect(store.others().map(s => s.id)).toEqual(['laptop']);

    api.signOutOthers.and.returnValue(of(undefined));
    expect(await store.signOutOthers()).toBe(1);
    expect(store.sessions().map(s => s.id)).toEqual(['here']);
    expect(store.ending()).toBeNull();
  });

  it('shows why a password change failed, and reloads sessions after one works', async () => {
    const store = TestBed.inject(AccountStore);
    api.changePassword.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Your current password is not correct.' },
    })));
    expect(await store.changePassword('wrong', 'letters-and-42')).toBeFalse();
    expect(store.passwordError()).toBe('Your current password is not correct.');

    api.changePassword.and.returnValue(of(undefined));
    api.sessions.and.returnValue(of([session('new-here', true)]));
    expect(await store.changePassword('right', 'letters-and-42')).toBeTrue();
    expect(store.passwordChanged()).toBeTrue();
    expect(store.passwordError()).toBe('');
    expect(store.sessions().map(s => s.id)).toEqual(['new-here']);
  });
});
