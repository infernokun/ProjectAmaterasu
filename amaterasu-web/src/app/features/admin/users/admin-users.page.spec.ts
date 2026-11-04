import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Role } from '../../../enums/role.enum';
import { AuthService } from '../../../services/auth.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { AdminUsersService } from './admin-users.service';
import { AdminUsersPage } from './admin-users.page';

describe('AdminUsersPage', () => {
  let fixture: ComponentFixture<AdminUsersPage>;
  let api: jasmine.SpyObj<AdminUsersService>;
  let confirm: jasmine.SpyObj<ConfirmService>;

  beforeEach(() => {
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1', username: 'sbell' }, 'ADMIN');
    api = jasmine.createSpyObj<AdminUsersService>('AdminUsersService', ['users', 'teams', 'teamLabs', 'setDisabled', 'changeRole']);
    api.users.and.returnValue(of([
      { id: 'u1', username: 'sbell', role: Role.ADMIN, disabled: false, team: null, lastSignIn: new Date().toISOString() },
      { id: 'u2', username: 'jmorales', role: Role.MEMBER, disabled: false, team: { id: 't1', name: 'Red Fox' }, lastSignIn: null },
      { id: 'u3', username: 'nsantos', role: Role.MEMBER, disabled: true, team: null, lastSignIn: null },
    ]));
    api.teams.and.returnValue(of([{ id: 't1', name: 'Red Fox' }]));
    api.teamLabs.and.returnValue(of([]));
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['open']);
    TestBed.configureTestingModule({
      imports: [AdminUsersPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AdminUsersService, useValue: api },
        { provide: ConfirmService, useValue: confirm },
      ],
    });
    fixture = TestBed.createComponent(AdminUsersPage);
    fixture.detectChanges();
  });

  const rows = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];
  const rowFor = (name: string) => rows().find(row => row.textContent!.includes(name))!;

  it('lists accounts with their state, and protects your own', () => {
    expect(fixture.nativeElement.textContent).toContain('3 accounts · 1 disabled · 1 team');
    const me = rowFor('sbell');
    expect(me.textContent).toContain('You');
    expect(me.textContent).toContain('Now');
    expect((me.querySelector('select[aria-label^="Role"]') as HTMLSelectElement).disabled).toBeTrue();
    const disableMine = Array.from(me.querySelectorAll('button')).find(b => b.textContent!.trim() === 'Disable')!;
    expect(disableMine.disabled).toBeTrue();

    expect(rowFor('jmorales').textContent).toContain('Never');
    expect(rowFor('nsantos').classList).toContain('dimrow');
    expect(rowFor('nsantos').textContent).toContain('Enable');
  });

  it('asks before disabling someone', async () => {
    confirm.open.and.returnValue(of('confirm'));
    api.setDisabled.and.returnValue(of({ id: 'u2', username: 'jmorales', role: Role.MEMBER, disabled: true, team: null }));
    const button = Array.from(rowFor('jmorales').querySelectorAll('button')).find(b => b.textContent!.trim() === 'Disable')!;
    button.click();
    await fixture.whenStable();
    expect(confirm.open).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Disable jmorales?', confirmLabel: 'Disable account' }));
    expect(api.setDisabled).toHaveBeenCalledWith('u2', true);
    fixture.detectChanges();
    expect(rowFor('jmorales').textContent).toContain('Disabled');
  });

  it('keeps the role when the change is cancelled', async () => {
    confirm.open.and.returnValue(of('cancel'));
    const select = rowFor('jmorales').querySelector('select[aria-label^="Role"]') as HTMLSelectElement;
    select.value = Role.CREATOR;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(api.changeRole).not.toHaveBeenCalled();
    expect(select.value).toBe(Role.MEMBER);
  });
});
