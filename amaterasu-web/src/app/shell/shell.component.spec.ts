import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { EMPTY, of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { RoomService } from '../services/ctf/room.service';
import { LabTrackerService } from '../services/lab/lab-tracker.service';
import { WebsocketService } from '../services/websocket.service';
import { FakeAuthService } from '../testing/fake-auth';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let auth: FakeAuthService;
  let fixture: ComponentFixture<ShellComponent>;

  beforeEach(() => {
    auth = new FakeAuthService();
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: auth },
        { provide: LabTrackerService, useValue: { getLabTrackersByTeam: () => of([]) } },
        { provide: RoomService, useValue: { getAllRooms: () => of({ data: [] }), checkJoinable: () => of({ data: {} }) } },
        { provide: WebsocketService, useValue: { getMessage: () => EMPTY } },
      ],
    });
  });

  function render(roles: string) {
    auth.signIn({ id: 'u1', username: 'jmorales', team: { id: 't1', name: 'Red Fox' } }, roles);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const groupLabels = (el: HTMLElement) => Array.from(el.querySelectorAll('.nav-label')).map(n => n.textContent?.trim());

  it('shows members only the Play group, plus Account and Sign out', () => {
    const el = render('MEMBER');
    expect(groupLabels(el)).toEqual(['Play']);
    expect(el.querySelector('.side-foot')?.textContent).toContain('Account');
    expect(el.querySelector('.side-foot')?.textContent).toContain('Sign out');
  });

  it('shows admins every group', () => {
    expect(groupLabels(render('ADMIN'))).toEqual(['Play', 'Run', 'Build', 'Admin']);
  });

  it('names the user, team and role in the top bar', () => {
    const el = render('CREATOR');
    expect(el.querySelector('.me-name')?.textContent).toContain('jmorales');
    expect(el.querySelector('.me-meta')?.textContent).toContain('Red Fox · Creator');
    expect(el.querySelector('.avatar')?.textContent).toContain('JM');
  });

  it('signs out from the sidebar', () => {
    const el = render('MEMBER');
    const signOut = Array.from(el.querySelectorAll<HTMLButtonElement>('.side-foot button')).find(b => b.textContent?.includes('Sign out'))!;
    signOut.click();
    expect(auth.logout).toHaveBeenCalled();
  });
});
