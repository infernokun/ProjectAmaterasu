import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { Role } from '../../../enums/role.enum';
import { AdminUser, AdminUsersService } from './admin-users.service';
import { UsersAdminStore } from './users-admin.store';

const user = (id: string, role: Role, teamId: string | null, disabled = false): AdminUser => ({
  id, username: id, role, disabled, team: teamId ? { id: teamId, name: teamId === 't1' ? 'Red Fox' : 'Night Owls' } : null,
});

describe('UsersAdminStore', () => {
  let api: jasmine.SpyObj<AdminUsersService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<AdminUsersService>('AdminUsersService', [
      'users', 'teams', 'teamLabs', 'createUser', 'changeRole', 'setTeam', 'setDisabled', 'resetPassword', 'createTeam', 'editTeam', 'deleteTeam',
      'setTeamMany', 'importUsers',
    ]);
    api.users.and.returnValue(of([
      user('sbell', Role.ADMIN, 't1'), user('jmorales', Role.MEMBER, 't1'), user('ghost_rk', Role.MEMBER, 't2', true), user('guest04', Role.MEMBER, null),
    ]));
    api.teams.and.returnValue(of([{ id: 't2', name: 'Night Owls' }, { id: 't1', name: 'Red Fox' }]));
    api.teamLabs.and.returnValue(of([
      { id: 'a', labStatus: LabStatus.ACTIVE, teamId: 't1', labName: 'Web' },
      { id: 'b', labStatus: LabStatus.PAUSED, teamId: 't1', labName: 'AD' },
      { id: 'c', labStatus: LabStatus.STOPPED, teamId: 't2', labName: 'Web' },
    ]));
    TestBed.configureTestingModule({ providers: [UsersAdminStore, { provide: AdminUsersService, useValue: api }] });
  });

  function loaded() {
    const store = TestBed.inject(UsersAdminStore);
    store.load();
    return store;
  }

  it('loads users sorted by name and filters them', () => {
    const store = loaded();
    expect(store.users().map(u => u.username)).toEqual(['ghost_rk', 'guest04', 'jmorales', 'sbell']);
    expect(store.disabledCount()).toBe(1);

    store.setQuery('red fox');
    expect(store.filtered().map(u => u.id)).toEqual(['jmorales', 'sbell']);
    store.setQuery('');
    store.setRoleFilter(Role.ADMIN);
    expect(store.filtered().map(u => u.id)).toEqual(['sbell']);
    store.setRoleFilter('ALL');
    store.setDisabledOnly(true);
    expect(store.filtered().map(u => u.id)).toEqual(['ghost_rk']);
  });

  it('counts members and labs per team', () => {
    const store = loaded();
    const [nightOwls, redFox] = store.teamCards();
    expect(redFox.team.name).toBe('Red Fox');
    expect(redFox.members.length).toBe(2);
    expect(redFox.running).toBe(1);
    expect(redFox.deployed).toBe(2);
    expect(nightOwls.deployed).toBe(0);
  });

  it('applies changes from the API to the list', async () => {
    const store = loaded();
    api.changeRole.and.returnValue(of(user('jmorales', Role.CREATOR, 't1')));
    await store.changeRole('jmorales', Role.CREATOR);
    expect(store.users().find(u => u.id === 'jmorales')?.role).toBe(Role.CREATOR);

    api.createUser.and.returnValue(of(user('aokafor', Role.CREATOR, null)));
    await store.createUser({ username: 'aokafor', password: 'x', role: Role.CREATOR, teamId: null });
    expect(store.users()[0].id).toBe('aokafor');

    api.deleteTeam.and.returnValue(of({}));
    await store.deleteTeam('t1');
    expect(store.teams().map(t => t.id)).toEqual(['t2']);
    expect(store.users().find(u => u.id === 'jmorales')?.team).toBeNull();
  });

  it('runs bulk actions on the selection, skipping who it is told to', async () => {
    const store = loaded();
    store.toggleSelected('sbell');
    store.toggleSelected('jmorales');
    store.toggleSelected('guest04');
    api.setDisabled.and.callFake((id: string) => (id === 'guest04'
      ? throwError(() => new Error('nope'))
      : of(user(id, Role.MEMBER, 't1', true))));

    const result = await store.forSelected(id => store.setDisabled(id, true), ['sbell']);

    expect(api.setDisabled).toHaveBeenCalledTimes(2);
    expect(result.done).toBe(1);
    expect(result.failed).toBeTruthy();
    expect(store.selected()).toEqual([]);
  });

  it('moves the whole selection in one request', async () => {
    const store = loaded();
    store.toggleSelected('jmorales');
    store.toggleSelected('guest04');
    api.setTeamMany.and.returnValue(of([user('jmorales', Role.MEMBER, 't2'), user('guest04', Role.MEMBER, 't2')]));

    const moved = await store.moveSelected('t2');

    expect(api.setTeamMany).toHaveBeenCalledOnceWith(['jmorales', 'guest04'], 't2');
    expect(moved.length).toBe(2);
    expect(store.users().find(u => u.id === 'guest04')?.team?.name).toBe('Night Owls');
    expect(store.selected()).toEqual([]);
  });

  it('keeps the selection when a bulk move fails, since nobody moved', async () => {
    const store = loaded();
    store.toggleSelected('jmorales');
    api.setTeamMany.and.returnValue(throwError(() => new Error('nope')));
    await expectAsync(store.moveSelected(null)).toBeRejected();
    expect(store.selected()).toEqual(['jmorales']);
    expect(store.users().find(u => u.id === 'jmorales')?.team?.id).toBe('t1');
  });

  it('imports rows with team names and reloads the users it created', async () => {
    const store = loaded();
    api.importUsers.and.returnValue(of({ created: [{ row: 2, id: 'n1', username: 'lchen', role: Role.MEMBER, team: 'Red Fox' }], skipped: [] }));
    api.users.and.returnValue(of([user('lchen', Role.MEMBER, 't1'), user('sbell', Role.ADMIN, 't1')]));

    const result = await store.importUsers([{
      line: 2, username: 'lchen', role: Role.MEMBER, team: { id: 't1', name: 'Red Fox' }, password: 'p', generatedPassword: true, problems: [],
    }]);

    expect(api.importUsers).toHaveBeenCalledWith([{ row: 2, username: 'lchen', password: 'p', role: Role.MEMBER, team: 'Red Fox' }]);
    expect(result.created.length).toBe(1);
    expect(store.users().map(u => u.id)).toEqual(['lchen', 'sbell']);
  });

  it('reports a failed load', () => {
    api.users.and.returnValue(throwError(() => new Error('down')));
    const store = loaded();
    expect(store.status()).toBe('error');
    expect(store.error()).toBe('Users and teams could not be loaded.');
  });
});
