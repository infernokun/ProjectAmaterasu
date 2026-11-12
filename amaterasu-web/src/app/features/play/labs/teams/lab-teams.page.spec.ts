import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../services/auth.service';
import { WebsocketService } from '../../../../services/websocket.service';
import { ConfirmService } from '../../../../shared/services/confirm.service';
import { ToastService } from '../../../../shared/services/toast.service';
import { FakeAuthService } from '../../../../testing/fake-auth';
import { LabManageApi } from '../manage/lab-manage-api.service';
import { LabTeamsView, TeamCandidate } from '../manage/lab-manage.models';
import { LabTeamsPage } from './lab-teams.page';

const view = (overrides: Partial<LabTeamsView> = {}): LabTeamsView => ({
  roomId: 'r1', roomName: 'Winter Range', teamMode: 'SITE', teamCredit: 'SEE_TEAMMATES', environmentMode: 'PER_TEAM',
  labTeams: [],
  siteTeams: [{ id: 's1', name: 'Night Owls', description: null, copyStatus: 'RUNNING',
    members: [{ userId: 'u1', username: 'hwalsh', displayName: null }, { userId: 'u2', username: 'dkim', displayName: null }] }],
  ...overrides,
});

const labView = () => view({
  teamMode: 'LAB',
  labTeams: [{ id: 't1', name: 'Red', description: null, copyStatus: 'NOT_RUNNING',
    members: [{ userId: 'u1', username: 'hwalsh', displayName: 'Hana Walsh' }] }],
});

const people: TeamCandidate[] = [
  { userId: 'u1', username: 'hwalsh', displayName: 'Hana Walsh', role: 'MEMBER', siteTeamName: 'Night Owls', labTeamId: 't1', labTeamName: 'Red', played: true },
  { userId: 'u3', username: 'nsantos', displayName: null, role: 'MEMBER', siteTeamName: null, labTeamId: null, labTeamName: null, played: true },
  { userId: 'u4', username: 'jmorales', displayName: null, role: 'MEMBER', siteTeamName: null, labTeamId: null, labTeamName: null, played: false },
];

describe('LabTeamsPage', () => {
  let fixture: ComponentFixture<LabTeamsPage>;
  let api: jasmine.SpyObj<LabManageApi>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let toast: jasmine.SpyObj<ToastService>;
  const auth = new FakeAuthService();

  async function create(initial: LabTeamsView, role = 'ADMIN') {
    auth.signIn({ id: 'me', username: 'sbell' }, role);
    api = jasmine.createSpyObj<LabManageApi>('LabManageApi', ['teams', 'candidates', 'setTeamMode', 'createTeam', 'editTeam',
      'deleteTeam', 'addMembers', 'removeMember', 'copySiteTeams']);
    api.teams.and.returnValue(of(initial));
    api.candidates.and.returnValue(of(people));
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['open']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    TestBed.configureTestingModule({
      imports: [LabTeamsPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LabManageApi, useValue: api },
        { provide: ConfirmService, useValue: confirm },
        { provide: ToastService, useValue: toast },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
      ],
    });
    fixture = TestBed.createComponent(LabTeamsPage);
    fixture.componentRef.setInput('labId', 'r1');
    await settle();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (text: string) => [...el().querySelectorAll('button')].find(b => b.textContent!.trim() === text)!;

  it('shows the site teams read-only, with the way to change them for admins', async () => {
    await create(view());
    expect(api.teams).toHaveBeenCalledWith('r1');
    expect(el().textContent).toContain('Night Owls');
    expect(el().textContent).toContain('hwalsh, dkim');
    const link = el().querySelector('a[href^="/admin/users"]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/admin/users?tab=teams');
    expect(button('Add team')).toBeUndefined();
    expect(el().querySelector('[role=radio][aria-checked=true]')!.textContent).toContain('Site teams');
  });

  it('tells facilitators who changes site teams', async () => {
    await create(view(), 'FACILITATOR');
    expect(el().querySelector('a[href^="/admin/users"]')).toBeNull();
    expect(el().textContent).toContain('Admins change site teams in Admin › Users and teams.');
  });

  it('asks before switching mode, then shows the lab teams', async () => {
    await create(view());
    confirm.open.and.returnValue(of('confirm'));
    api.setTeamMode.and.returnValue(of(labView()));
    const lab = [...el().querySelectorAll<HTMLButtonElement>('[role=radio]')].find(b => b.textContent!.includes('Lab teams'))!;
    lab.click();
    await settle();
    expect(confirm.open).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Use lab teams in Winter Range?' }));
    expect(api.setTeamMode).toHaveBeenCalledWith('r1', 'LAB');
    expect(el().textContent).toContain('Red');
    expect(el().textContent).toContain('Hana Walsh');
  });

  it('keeps the mode when the switch is cancelled', async () => {
    await create(view());
    confirm.open.and.returnValue(of('cancel'));
    [...el().querySelectorAll<HTMLButtonElement>('[role=radio]')].find(b => b.textContent!.includes('No teams'))!.click();
    await settle();
    expect(api.setTeamMode).not.toHaveBeenCalled();
  });

  it('adds a team, people to it and takes them off', async () => {
    await create(labView());
    api.createTeam.and.returnValue(of(labView()));
    const name = el().querySelector<HTMLInputElement>('#new-team')!;
    name.value = 'Blue';
    name.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button('Add team').click();
    await settle();
    expect(api.createTeam).toHaveBeenCalledWith('r1', 'Blue');
    expect(toast.success).toHaveBeenCalledWith('Blue added');

    // The picker leaves out people already on the team and moves people from another
    button('Add people').click();
    fixture.detectChanges();
    const matches = [...el().querySelectorAll('.match')].map(row => row.textContent!);
    expect(matches.some(text => text.includes('hwalsh'))).toBeFalse();
    expect(matches[0]).toContain('nsantos');
    api.addMembers.and.returnValue(of(labView()));
    (el().querySelector('.match button') as HTMLButtonElement).click();
    await settle();
    expect(api.addMembers).toHaveBeenCalledWith('r1', 't1', ['u3']);

    api.removeMember.and.returnValue(of(labView()));
    (el().querySelector('button[aria-label="Take hwalsh off Red"]') as HTMLButtonElement).click();
    await settle();
    expect(api.removeMember).toHaveBeenCalledWith('r1', 't1', 'u1');
  });

  it('copies the site teams and lists players without a team', async () => {
    await create(labView());
    expect(el().textContent).toContain('Played, but on no team here');
    expect(el().textContent).toContain('nsantos');
    api.copySiteTeams.and.returnValue(of({ data: labView(), message: '1 teams made, 2 people added' }));
    button('Copy from site teams').click();
    await settle();
    expect(api.copySiteTeams).toHaveBeenCalledWith('r1');
    expect(toast.success).toHaveBeenCalledWith('1 teams made, 2 people added');
  });

  it('deletes a team after confirming', async () => {
    await create(labView());
    confirm.open.and.returnValue(of('confirm'));
    api.deleteTeam.and.returnValue(of(view({ teamMode: 'LAB' })));
    button('Delete').click();
    await settle();
    expect(confirm.open).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Delete Red?', confirmLabel: 'Delete team' }));
    expect(api.deleteTeam).toHaveBeenCalledWith('r1', 't1');
    expect(el().textContent).toContain('No lab teams yet');
  });

  it('tells people who do not run the lab', async () => {
    auth.signIn({ id: 'me', username: 'sbell' }, 'MEMBER');
    api = jasmine.createSpyObj<LabManageApi>('LabManageApi', ['teams', 'candidates']);
    api.teams.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Access denied' } })));
    TestBed.configureTestingModule({
      imports: [LabTeamsPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LabManageApi, useValue: api },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
      ],
    });
    fixture = TestBed.createComponent(LabTeamsPage);
    fixture.componentRef.setInput('labId', 'r1');
    await settle();
    expect(el().textContent).toContain('Only the lab\'s facilitators and admins manage its teams');
    expect(el().querySelector('[role=radio]')).toBeNull();
  });
});
