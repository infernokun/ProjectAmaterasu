import { TestBed } from '@angular/core/testing';
import { Role } from '../../enums/role.enum';
import { AuthService } from '../../services/auth.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { SessionStore, initialsOf } from './session.store';

describe('SessionStore', () => {
  let auth: FakeAuthService;
  let store: InstanceType<typeof SessionStore>;

  beforeEach(() => {
    auth = new FakeAuthService();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
    store = TestBed.inject(SessionStore);
  });

  it('follows the signed-in user and the roles in their token', () => {
    expect(store.signedIn()).toBeFalse();
    auth.signIn({ id: 'u1', username: 'jmorales', team: { id: 't1', name: 'Red Fox' } }, 'FACILITATOR');

    expect(store.signedIn()).toBeTrue();
    expect(store.username()).toBe('jmorales');
    expect(store.teamId()).toBe('t1');
    expect(store.roles()).toEqual([Role.FACILITATOR]);
    expect(store.roleLabel()).toBe('Facilitator');
    expect(store.canRun()).toBeTrue();
    expect(store.canBuild()).toBeFalse();
    expect(store.isAdmin()).toBeFalse();
    expect(store.hasRole(Role.CREATOR, Role.FACILITATOR)).toBeTrue();
  });

  it('gives admins every area', () => {
    auth.signIn({ id: 'u1', username: 'sbell' }, 'ADMIN');
    expect(store.canRun()).toBeTrue();
    expect(store.canBuild()).toBeTrue();
    expect(store.isAdmin()).toBeTrue();
  });

  it('ignores roles the app does not know', () => {
    auth.signIn({ id: 'u1' }, 'MEMBER SUPERUSER');
    expect(store.roles()).toEqual([Role.MEMBER]);
  });

  it('clears on sign out and signs out through AuthService', () => {
    auth.signIn({ id: 'u1' }, 'MEMBER');
    store.signOut();
    expect(auth.logout).toHaveBeenCalled();
    auth.signOutNow();
    expect(store.signedIn()).toBeFalse();
    expect(store.roles()).toEqual([]);
  });

  it('makes initials from usernames', () => {
    expect(initialsOf('jmorales')).toBe('JM');
    expect(initialsOf('priya.n')).toBe('PN');
    expect(initialsOf(undefined)).toBe('');
  });
});
