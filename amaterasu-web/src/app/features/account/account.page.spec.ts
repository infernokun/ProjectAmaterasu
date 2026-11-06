import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Role } from '../../enums/role.enum';
import { AuthService } from '../../services/auth.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { AccountService } from './account.service';
import { AccountPage, capabilitiesFor } from './account.page';

describe('AccountPage', () => {
  let fixture: ComponentFixture<AccountPage>;
  let api: jasmine.SpyObj<AccountService>;
  let auth: FakeAuthService;

  beforeEach(() => {
    auth = new FakeAuthService();
    auth.signIn({ id: 'u1', username: 'jmorales', team: { id: 't1', name: 'Red Fox' } }, 'MEMBER');
    api = jasmine.createSpyObj<AccountService>('AccountService', ['sessions', 'signOut', 'signOutOthers', 'changePassword']);
    api.sessions.and.returnValue(of([]));
    api.changePassword.and.returnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [AccountPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }, { provide: AccountService, useValue: api }],
    });
    fixture = TestBed.createComponent(AccountPage);
  });

  function type(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('shows the profile with what the role allows', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('jmorales');
    expect(text).toContain('Role: Member');
    expect(text).toContain('Start, pause, resume and stop Red Fox labs');
  });

  it('enables Change password only when the rules pass and both entries match', () => {
    fixture.componentRef.setInput('section', 'password');
    fixture.detectChanges();
    const submit = () => fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement;
    expect(submit().disabled).toBeTrue();

    type('input[autocomplete=current-password]', 'old-password-1');
    const [next, confirm] = Array.from(fixture.nativeElement.querySelectorAll('input[autocomplete=new-password]')) as HTMLInputElement[];
    next.value = 'short';
    next.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(submit().disabled).toBeTrue();

    next.value = 'letters-and-42';
    next.dispatchEvent(new Event('input'));
    confirm.value = 'letters-and-4';
    confirm.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Does not match the new password');
    expect(submit().disabled).toBeTrue();

    confirm.value = 'letters-and-42';
    confirm.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(submit().disabled).toBeFalse();

    submit().click();
    expect(api.changePassword).toHaveBeenCalledWith('old-password-1', 'letters-and-42');
  });

  it('lists what each role can do', () => {
    expect(capabilitiesFor(Role.CREATOR, 'Staff').filter(c => c.can).map(c => c.text)).toContain('Author labs, rooms and challenges');
    expect(capabilitiesFor(Role.MEMBER, undefined)[1].can).toBeFalse();
    expect(capabilitiesFor(Role.ADMIN, 'Staff').every(c => c.can)).toBeTrue();
  });
});
