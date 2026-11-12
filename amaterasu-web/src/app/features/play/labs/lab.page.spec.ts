import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { CrumbsService } from '../../../core/crumbs.service';
import { KEEP_SCROLL } from '../../../core/page';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthService } from '../../../services/auth.service';
import { WebsocketService } from '../../../services/websocket.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { PlayApiService } from '../data/play-api.service';
import { ChallengeView, RoomOverview } from '../data/play.models';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { LabPage } from './lab.page';
import { LAB_SPLIT } from './lab-workspace';

/** Stands in for Details, Console and the other sub-pages. */
@Component({ selector: 'app-sub-stub', template: '<p class="sub-stub">Sub-page</p>' })
class SubStub {}

const question = (id: string, title: string): ChallengeView => ({ id, title, category: 'Web', difficulty: null, points: 100, author: null,
  maxAttempts: null, solves: 0, state: 'OPEN', releaseDate: null, expirationDate: null, description: 'Look', attachments: [], hints: [],
  attempts: 0, solved: false, solvedAt: null, solvedByTeammates: [] });

describe('LabPage', () => {
  let fixture: ComponentFixture<LabPage>;
  let overview: RoomOverview;

  async function render(patch: Partial<RoomOverview> = {}) {
    overview = {
      room: { id: 'r1', name: 'On Call', surroundTag: null, creator: null, facilitators: [], scenario: '## Situation\nThe shop is down.',
        environmentMode: 'PER_TEAM', canRun: false, teamCredit: 'SEE_TEAMMATES' },
      playerCount: 2, teamCount: 1, me: { points: 0, rank: 0, solved: 0, teamId: 'red', teamName: 'Red' },
      challenges: [question('q1', 'Find the log')],
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
        { provide: LAB_SPLIT, useValue: signal(false) },
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

  describe('picking a question', () => {
    async function renderAt(width: number) {
      await render({ challenges: [question('q1', 'Find the log'), question('q2', 'Read the dump')] });
      el().style.display = 'block';
      el().style.width = `${width}px`;
    }

    async function pickSecond() {
      const pick = [...el().querySelectorAll<HTMLButtonElement>('.list-btn')].find(b => b.textContent!.includes('Read the dump'))!;
      pick.click();
      fixture.detectChanges();
      await fixture.whenStable();
    }

    it('keeps the address in step without scrolling the page to the top', async () => {
      await renderAt(1200);
      const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      await pickSecond();
      expect(navigate).toHaveBeenCalledOnceWith([], { queryParams: { challenge: 'q2' }, replaceUrl: true, info: KEEP_SCROLL });
      expect(el().querySelector('#ch-title')!.textContent).toContain('Read the dump');
    });

    it('leaves the page where it is when the detail sits beside the list', async () => {
      await renderAt(1200);
      const scroll = spyOn(Element.prototype, 'scrollIntoView');
      await pickSecond();
      expect(scroll).not.toHaveBeenCalled();
    });

    it('scrolls only as far as the detail when it sits under the list', async () => {
      await renderAt(420);
      const scroll = spyOn(Element.prototype, 'scrollIntoView');
      await pickSecond();
      expect(scroll).toHaveBeenCalledTimes(1);
      expect(scroll.calls.mostRecent().object as Element).toBe(el().querySelector('app-challenge-detail')!);
      expect(scroll.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ block: 'nearest' }));
    });
  });

  it('forwards an old copy console address to the copy under its lab', async () => {
    const api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['overview', 'tracker']);
    api.overview.and.returnValue(throwError(() => new Error('404')));
    api.tracker.and.returnValue(of(new LabTracker({ id: 't1' })));
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1' }, 'MEMBER');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'labs/:labId', component: LabPage, children: [{ path: 'console/:vmid', component: SubStub }] }],
          withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
        { provide: PlayApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
        { provide: RoomsStore, useValue: { setCurrentRoom: jasmine.createSpy('setCurrentRoom') } },
        { provide: LAB_SPLIT, useValue: signal(false) },
      ],
    });
    const harness = await RouterTestingHarness.create();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    await harness.navigateByUrl('/labs/t1/console/101');
    for (let i = 0; i < 3; i++) {
      await new Promise(resolve => setTimeout(resolve));
      harness.fixture.detectChanges();
    }
    expect(navigate).toHaveBeenCalledWith(['/environments', 't1', 'console', '101'], { replaceUrl: true });
  });

  describe('workspace', () => {
    let harness: RouterTestingHarness;
    const split = signal(true);

    async function open(url: string, wide: boolean, roles = 'MEMBER', patch: Partial<RoomOverview> = {}) {
      split.set(wide);
      const room = { id: 'r1', name: 'On Call', surroundTag: null, creator: null, facilitators: [], scenario: 'The shop is down.',
        environmentMode: 'PER_TEAM' as const, canRun: roles !== 'MEMBER', teamCredit: 'SEE_TEAMMATES' as const };
      const data: RoomOverview = {
        room, playerCount: 2, teamCount: 1, me: { points: 0, rank: 0, solved: 0, teamId: null, teamName: null },
        challenges: [question('q1', 'Find the log'), question('q2', 'Read the dump')],
        environment: { labId: 'def1', labName: 'Shop', labType: 'DOCKER_COMPOSE', mode: 'PER_TEAM', status: 'RUNNING', trackerId: 't1',
          tracker: null, canControl: false, note: null, copiesRunning: 1, copies: [] },
        files: [], ...patch,
      };
      const api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['overview', 'standings', 'tracker']);
      api.overview.and.returnValue(of(data));
      api.standings.and.returnValue(of({ roomId: 'r1', roomName: 'On Call', players: [], teams: [] }));
      const auth = new FakeAuthService();
      auth.signIn({ id: 'u1' }, roles);
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{
            path: 'labs/:labId', component: LabPage, children: [
              { path: 'details', component: SubStub, data: { title: 'Details' } },
              { path: 'console/:vmid', component: SubStub, data: { title: 'Console' } },
              { path: 'teams', component: SubStub, data: { title: 'Teams' } },
            ],
          }], withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
          { provide: PlayApiService, useValue: api },
          { provide: AuthService, useValue: auth },
          { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
          { provide: RoomsStore, useValue: { setCurrentRoom: jasmine.createSpy('setCurrentRoom') } },
          { provide: LAB_SPLIT, useValue: split },
        ],
      });
      harness = await RouterTestingHarness.create();
      await go(url);
    }

    async function go(url: string) {
      await harness.navigateByUrl(url);
      await harness.fixture.whenStable();
      harness.fixture.detectChanges();
    }

    const page = () => harness.fixture.nativeElement as HTMLElement;
    const tab = (label: string) => [...page().querySelectorAll<HTMLAnchorElement>('.views .tab')].find(a => a.textContent!.trim() === label);

    it('shows the question beside the list on a wide screen', async () => {
      await open('/labs/r1?challenge=q2', true);
      expect(page().querySelector('.lab-pane app-challenge-detail')).toBeNull();
      expect(page().querySelector('.side-pane app-challenge-detail #ch-title')!.textContent).toContain('Read the dump');
      expect(tab('Questions')!.getAttribute('aria-current')).toBe('page');
    });

    it('opens a sub-page in the right pane, beside the questions, under a breadcrumb back to the lab', async () => {
      await open('/labs/r1/details', true);
      expect(page().querySelector('.lab-pane .challenge-list')).not.toBeNull();
      expect(page().querySelector('.side-pane .sub-stub')).not.toBeNull();
      expect(page().querySelector('.side-pane app-challenge-detail')).toBeNull();
      expect(page().querySelector('#lab-pane-h')!.textContent).toContain('Details');
      expect(page().querySelector('.side-pane')!.getAttribute('aria-labelledby')).toBe('lab-pane-h');
      expect(tab('Details')!.getAttribute('aria-current')).toBe('page');
      expect(tab('Questions')!.getAttribute('aria-current')).toBeNull();
      expect(TestBed.inject(CrumbsService).custom()).toEqual([
        { label: 'Labs', link: '/labs' }, { label: 'On Call', link: ['/labs', 'r1'] }, { label: 'Details' },
      ]);
    });

    it('gives a sub-page the whole page with a Back to the lab on a narrow screen', async () => {
      await open('/labs/r1/details', false);
      expect(page().querySelector('.lab-pane')).toBeNull();
      expect(page().querySelector('.page-head')).toBeNull();
      expect(page().querySelector('.side-pane .sub-stub')).not.toBeNull();
      const back = page().querySelector<HTMLAnchorElement>('.pane-head a.back')!;
      expect(back.textContent).toContain('On Call');
      expect(back.getAttribute('aria-label')).toBe('Back to On Call');
      expect(back.getAttribute('href')).toBe('/labs/r1?challenge=q1');
      expect([...page().querySelectorAll('button')].some(b => b.textContent!.includes('Pop out'))).toBeFalse();
    });

    it('keeps the question under the list on a narrow screen', async () => {
      await open('/labs/r1', false);
      expect(page().querySelector('.lab-pane app-challenge-detail')).not.toBeNull();
      expect(page().querySelector('.side-pane')!.classList).toContain('idle');
    });

    it('pops a sub-page out to the whole page and back', async () => {
      await open('/labs/r1/console/101', true);
      const popOut = [...page().querySelectorAll('button')].find(b => b.textContent!.includes('Pop out'))!;
      popOut.click();
      await harness.fixture.whenStable();
      harness.fixture.detectChanges();
      expect(TestBed.inject(Router).url).toBe('/labs/r1/console/101?full=1');
      expect(page().querySelector('.lab-pane')).toBeNull();
      expect(page().querySelector('.side-pane .sub-stub')).not.toBeNull();
      expect([...page().querySelectorAll('button')].some(b => b.textContent!.includes('Beside the questions'))).toBeTrue();
    });

    it('keeps the same sub-page when the screen narrows, so a console stays connected', async () => {
      await open('/labs/r1/console/101', true);
      const stub = page().querySelector('.sub-stub');
      split.set(false);
      harness.fixture.detectChanges();
      expect(page().querySelector('.lab-pane')).toBeNull();
      expect(page().querySelector('.sub-stub')).toBe(stub);
    });

    it('moves focus to the opened pane, and back to the lab on narrow screens', async () => {
      await open('/labs/r1', false);
      tab('Details')!.click();
      await harness.fixture.whenStable();
      harness.fixture.detectChanges();
      await harness.fixture.whenStable();
      expect(document.activeElement?.id).toBe('lab-pane-h');
      page().querySelector<HTMLAnchorElement>('.pane-head a.back')!.click();
      await harness.fixture.whenStable();
      harness.fixture.detectChanges();
      await harness.fixture.whenStable();
      expect(document.activeElement?.id).toBe('lab-h');
    });

    it('leaves a sub-page for the question picked on the left, without scrolling', async () => {
      await open('/labs/r1/details', true);
      const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      [...page().querySelectorAll<HTMLButtonElement>('.list-btn')].find(b => b.textContent!.includes('Read the dump'))!.click();
      expect(navigate).toHaveBeenCalledOnceWith(['/labs', 'r1'], { queryParams: { challenge: 'q2' }, info: KEEP_SCROLL });
    });

    it('shows Teams and Manage only to those who run the lab', async () => {
      await open('/labs/r1', true, 'MEMBER');
      expect(tab('Teams')).toBeUndefined();
      expect(tab('Details')).toBeDefined();
      TestBed.resetTestingModule();
      await open('/labs/r1', true, 'FACILITATOR');
      expect(tab('Teams')).toBeDefined();
      expect(tab('Manage')).toBeDefined();
      expect(tab('Edit')).toBeUndefined();
    });
  });
});
