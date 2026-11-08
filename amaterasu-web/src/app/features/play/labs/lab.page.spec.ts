import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthService } from '../../../services/auth.service';
import { WebsocketService } from '../../../services/websocket.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { PlayApiService } from '../data/play-api.service';
import { RoomOverview } from '../data/play.models';
import { LabPage } from './lab.page';

describe('LabPage', () => {
  let fixture: ComponentFixture<LabPage>;
  let overview: RoomOverview;

  async function render(patch: Partial<RoomOverview> = {}) {
    overview = {
      room: { id: 'r1', name: 'On Call', surroundTag: null, creator: null, facilitators: [], scenario: '## Situation\nThe shop is down.',
        environmentMode: 'PER_TEAM', canRun: false, teamCredit: 'SEE_TEAMMATES' },
      playerCount: 2, teamCount: 1, me: { points: 0, rank: 0, solved: 0, teamId: 'red', teamName: 'Red' },
      challenges: [{ id: 'q1', title: 'Find the log', category: 'Web', difficulty: null, points: 100, author: null, maxAttempts: null,
        solves: 0, state: 'OPEN', releaseDate: null, expirationDate: null, description: 'Look', attachments: [], hints: [], attempts: 0,
        solved: false, solvedAt: null, solvedByTeammates: [] }],
      environment: { labId: 'def1', labName: 'Shop', labType: 'DOCKER_COMPOSE', mode: 'PER_TEAM', status: 'NOT_RUNNING', trackerId: null,
        tracker: null, copyLabel: "Red's copy", canControl: false, note: 'Not running yet: an admin starts this environment.',
        copiesRunning: 0, copies: [] },
      files: [],
      ...patch,
    };
    const api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['overview', 'standings', 'tracker']);
    api.overview.and.returnValue(of(overview));
    api.standings.and.returnValue(of({ roomId: 'r1', roomName: 'On Call', players: [], teams: [] }));
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1' }, 'MEMBER');
    TestBed.configureTestingModule({
      imports: [LabPage],
      providers: [
        provideRouter([]),
        { provide: PlayApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
        { provide: RoomsStore, useValue: { setCurrentRoom: jasmine.createSpy('setCurrentRoom') } },
      ],
    });
    fixture = TestBed.createComponent(LabPage);
    fixture.componentRef.setInput('labId', 'r1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const buttons = () => [...el().querySelectorAll('button')].map(b => b.textContent!.trim());

  it('opens without joining: no Join, no team picker, and the answer box is there', async () => {
    await render();
    expect(el().textContent).toContain('On Call');
    expect(buttons().some(label => /join/i.test(label))).toBeFalse();
    expect(el().textContent).not.toContain('Join or create a team');
    expect(el().textContent).not.toContain('You play solo');
    expect(el().querySelector('app-challenge-detail input, app-challenge-detail textarea')).not.toBeNull();
  });

  it('puts the environment above the scenario and shows the team quietly in the header', async () => {
    await render();
    const env = el().querySelector('app-environment-bar')!;
    const scenario = el().querySelector('app-scenario-card')!;
    expect(env.compareDocumentPosition(scenario) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(el().querySelector('.page-head')!.textContent).toContain('Team Red');
    expect(el().textContent).toContain('Not running yet: an admin starts this environment.');
    expect(buttons()).not.toContain('Start');
  });

  it('shows no team tag for a player without a team', async () => {
    await render({ me: { points: 0, rank: 0, solved: 0, teamId: null, teamName: null } });
    expect(el().querySelector('.page-head')!.textContent).not.toContain('Team ');
  });
});
