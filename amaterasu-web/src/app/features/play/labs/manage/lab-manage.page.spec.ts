import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../../../services/auth.service';
import { WebsocketService } from '../../../../services/websocket.service';
import { ToastService } from '../../../../shared/services/toast.service';
import { FakeAuthService } from '../../../../testing/fake-auth';
import { AwardDialogComponent } from '../../../run/live/award-dialog.component';
import { LabManageApi } from './lab-manage-api.service';
import { LabTeamsView, ManageOverview, ManagePlayers, PlayerDetail, QuestionStats, Submission, SubmissionPage } from './lab-manage.models';
import { LabManagePage } from './lab-manage.page';
import { AcceptAnswerDialogComponent, CreditDialogComponent, ResetDialogComponent } from './manage-dialogs';

const overview: ManageOverview = {
  roomId: 'r1', roomName: 'Winter Range', teamMode: 'SITE', teamCredit: 'SHARED_SOLVES', environmentMode: 'NONE', players: 4,
  activePlayers: 3, teams: 1, questions: 2, solves: 2, attempts: 9, wrongAttempts: 7, nearMisses: 2, hintsUsed: 1, hintPoints: 10,
  firstActivityAt: '2026-11-12T09:00:00Z', bucketMinutes: 15,
  solvesOverTime: [{ at: '2026-11-12T09:00:00Z', solves: 1, attempts: 4 }, { at: '2026-11-12T09:15:00Z', solves: 1, attempts: 5 }],
  questionRates: [{ id: 'q1', title: 'Find the admin', points: 50, solves: 2, attemptedBy: 4, attempts: 9, solveRate: 50, hintsUsed: 1 }],
  copies: [],
};

const near: Submission = {
  id: 'a1:0', answerId: 'a1', index: 0, userId: 'u2', username: 'hwalsh', teamId: 't1', teamName: 'Night Owls', questionId: 'q1',
  questionTitle: 'Find the admin', questionPoints: 50, flag: 'admin', at: '2026-11-12T09:05:00Z', correct: false, creditedBy: null,
  questionSolved: false, nearMiss: { flagId: 'f1', flag: 'Admin', reasons: ['Wrong letter case'] },
};
const right: Submission = { ...near, id: 'a2:0', answerId: 'a2', userId: 'u3', username: 'dkim', teamId: null, teamName: null, flag: 'Admin',
  correct: true, questionSolved: true, nearMiss: null };
const page: SubmissionPage = { items: [near, right], page: 0, size: 50, total: 2, nearMisses: 1 };

const players: ManagePlayers = {
  players: [{ userId: 'u2', username: 'hwalsh', displayName: null, teamId: 't1', teamName: 'Night Owls', points: 40, solves: 0,
    attempts: 3, wrongAttempts: 3, hintsUsed: 1, hintPoints: 10, firstActivityAt: null, lastActivityAt: '2026-11-12T09:05:00Z' }],
  teams: [{ teamId: 't1', name: 'Night Owls', members: ['hwalsh'], points: 90, awardPoints: 50, solves: 0, attempts: 3, hintsUsed: 1 }],
};

const detail: PlayerDetail = {
  player: players.players[0],
  questions: [{ questionId: 'q1', title: 'Find the admin', points: 50, solved: false, solvedAt: null, score: 0, attempts: 3, hintsUsed: 1,
    hintPoints: 10, creditedBy: null, solvedByTeammate: null }],
  submissions: [near],
  history: [{ timestamp: '2026-11-12T09:01:00Z', totalPoints: 40, pointsChange: -10, reason: 'hint used', awardedBy: null, kind: null }],
};

const questions: QuestionStats[] = [{ id: 'q1', title: 'Find the admin', category: 'Web', points: 50, attempts: 9, attemptedBy: 4, solves: 2,
  solveRate: 50, hintsUsed: 1, nearMisses: 2,
  wrongAnswers: [{ answer: 'admin', count: 3, players: 2, nearMiss: near.nearMiss }, { answer: 'root', count: 1, players: 1, nearMiss: null }] }];

describe('LabManagePage', () => {
  let fixture: ComponentFixture<LabManagePage>;
  let api: jasmine.SpyObj<LabManageApi>;
  let dialog: { open: jasmine.Spy };
  let socket: Subject<unknown>;
  const auth = new FakeAuthService();

  async function create(role = 'FACILITATOR',
                        teams: Observable<LabTeamsView> = of({ roomName: 'Winter Range', teamCredit: 'SHARED_SOLVES' } as LabTeamsView)) {
    auth.signIn({ id: 'f1', username: 'dkim' }, role);
    api = jasmine.createSpyObj<LabManageApi>('LabManageApi', ['teams', 'overview', 'submissions', 'players', 'player', 'questions',
      'activity']);
    api.teams.and.returnValue(teams);
    api.overview.and.returnValue(of(overview));
    api.submissions.and.returnValue(of(page));
    api.players.and.returnValue(of(players));
    api.player.and.returnValue(of(detail));
    api.questions.and.returnValue(of(questions));
    api.activity.and.returnValue(of([
      { kind: 'SOLVE', at: '2026-11-12T09:10:00Z', who: 'dkim', team: null, text: 'solved Find the admin', detail: null, points: 50 },
      { kind: 'CREDIT', at: '2026-11-12T09:12:00Z', who: 'sbell', team: 'Night Owls', text: 'gave hwalsh credit', detail: 'Case slip', points: 50 },
      { kind: 'ENVIRONMENT', at: '2026-11-12T08:00:00Z', who: 'sbell', team: null, text: 'started Hunt', detail: null, points: null },
    ]));
    dialog = { open: jasmine.createSpy('open').and.returnValue({ afterClosed: () => of('Done') }) };
    socket = new Subject();
    TestBed.configureTestingModule({
      imports: [LabManagePage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LabManageApi, useValue: api },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']) },
        { provide: WebsocketService, useValue: { getMessage: () => socket.asObservable() } },
      ],
    });
    fixture = TestBed.createComponent(LabManagePage);
    fixture.componentRef.setInput('labId', 'r1');
    await settle();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (text: string, root: ParentNode = el()) => [...root.querySelectorAll('button')].find(b => b.textContent!.trim() === text)!;
  const tab = async (name: string) => {
    button(name).click();
    await settle();
  };

  it('opens on the overview with who is playing and the near misses', async () => {
    await create();
    expect(el().textContent).toContain('Winter Range');
    expect([...el().querySelectorAll('[role=tab]')].map(t => t.textContent!.trim()))
      .toEqual(['Overview', 'Submissions', 'Players', 'Questions', 'Activity']);
    expect(el().textContent).toContain('3 / 4');
    expect(el().textContent).toContain('Find the admin');
    expect(el().querySelector('app-points-chart')).not.toBeNull();
  });

  it('marks near misses in Submissions and gives credit for one', async () => {
    await create();
    await tab('Submissions');
    const row = el().querySelector('tr.near')!;
    expect(row.textContent).toContain('Near miss');
    expect(row.textContent).toContain('Wrong letter case');
    expect(el().querySelectorAll('tr.near').length).toBe(1);
    api.overview.calls.reset();
    api.submissions.calls.reset();

    button('Give credit', row).click();
    await settle();
    expect(dialog.open).toHaveBeenCalledWith(CreditDialogComponent, jasmine.objectContaining({
      data: jasmine.objectContaining({ labId: 'r1', submissionId: 'a1:0', teamCredit: 'SHARED_SOLVES', nearMiss: near.nearMiss }),
    }));
    // The change reloads the open tab
    expect(api.submissions).toHaveBeenCalled();
  });

  it('accepts an answer from a submission', async () => {
    await create();
    await tab('Submissions');
    button('Accept this answer', el().querySelector('tr.near')!).click();
    await settle();
    expect(dialog.open).toHaveBeenCalledWith(AcceptAnswerDialogComponent, jasmine.objectContaining({
      data: jasmine.objectContaining({ questionId: 'q1', answer: 'admin', submissionId: 'a1:0' }),
    }));
  });

  it('filters submissions by result', async () => {
    await create();
    await tab('Submissions');
    button('Near misses').click();
    await settle();
    expect(api.submissions).toHaveBeenCalledWith('r1', jasmine.objectContaining({ result: 'near', page: 0 }));
  });

  it('drills into a player and resets them only by name', async () => {
    await create();
    await tab('Players');
    expect(el().textContent).toContain('Night Owls');
    (el().querySelector('tbody tr.click') as HTMLElement).click();
    await settle();
    expect(api.player).toHaveBeenCalledWith('r1', 'u2');
    expect(el().textContent).toContain('Points history');
    button('Reset lab').click();
    await settle();
    expect(dialog.open).toHaveBeenCalledWith(ResetDialogComponent, jasmine.objectContaining({
      data: jasmine.objectContaining({ userId: 'u2', username: 'hwalsh' }),
    }));
    button('All players').click();
    await settle();
    expect(el().textContent).toContain('Award points');
  });

  it('awards a team from the Teams list', async () => {
    await create();
    await tab('Players');
    button('Award').click();
    await settle();
    expect(dialog.open).toHaveBeenCalledWith(AwardDialogComponent, jasmine.objectContaining({
      data: jasmine.objectContaining({ roomId: 'r1', teamId: 't1' }),
    }));
  });

  it('shows common wrong answers and links authors to edit the question', async () => {
    await create('ADMIN');
    await tab('Questions');
    expect(el().textContent).toContain('Most common wrong answers');
    expect(el().textContent).toContain('3× by 2');
    expect(el().querySelector('a[href^="/build/labs/r1"]')).not.toBeNull();
    button('Accept').click();
    await settle();
    expect(dialog.open).toHaveBeenCalledWith(AcceptAnswerDialogComponent, jasmine.objectContaining({
      data: jasmine.objectContaining({ answer: 'admin', players: 2 }),
    }));
  });

  it('hides question editing from facilitators', async () => {
    await create('FACILITATOR');
    await tab('Questions');
    expect(el().querySelector('a[href^="/build/labs/r1"]')).toBeNull();
  });

  it('filters the activity feed', async () => {
    await create();
    await tab('Activity');
    expect(el().querySelectorAll('.feed-item').length).toBe(3);
    button('Staff').click();
    await settle();
    expect(el().querySelectorAll('.feed-item').length).toBe(1);
    expect(el().textContent).toContain('gave hwalsh credit');
  });

  it('reloads when the lab changes elsewhere', async () => {
    await create();
    api.overview.calls.reset();
    socket.next({ type: 'roomActivity', roomId: 'other' });
    socket.next({ type: 'roomActivity', roomId: 'r1' });
    await new Promise(resolve => setTimeout(resolve, 900));
    await settle();
    expect(api.overview).toHaveBeenCalledTimes(1);
  });

  it('tells people who do not run the lab', async () => {
    await create('MEMBER', throwError(() => new HttpErrorResponse({ status: 403 })));
    expect(el().textContent).toContain('Only the lab\'s facilitators and admins manage it');
    expect(el().querySelector('[role=tab]')).toBeNull();
  });
});
