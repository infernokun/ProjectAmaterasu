import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthService } from '../../../services/auth.service';
import { WebsocketService } from '../../../services/websocket.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentView, LabCard } from '../data/play.models';
import { LabsPage } from './labs.page';

describe('LabsPage', () => {
  let fixture: ComponentFixture<LabsPage>;
  let api: jasmine.SpyObj<PlayApiService>;

  const env = (mode: 'SHARED' | 'PER_TEAM', canControl: boolean): EnvironmentView => ({
    labId: 'def', labName: 'Box', labType: 'VIRTUAL_MACHINE', mode, status: 'NOT_RUNNING', trackerId: null, tracker: null,
    copyLabel: mode === 'SHARED' ? 'Shared copy' : 'Solo copy', canControl, note: null, copiesRunning: 0, copies: [],
  });
  const card = (id: string, mode: 'NONE' | 'SHARED' | 'PER_TEAM', canRun: boolean): LabCard => ({
    id, name: `Lab ${id}`, summary: '', environmentMode: mode, environment: mode === 'NONE' ? null : env(mode, canRun),
    questionCount: 2, playerCount: 0, points: 0, solved: 0, canRun, teamId: null, teamName: null,
  });

  async function render(cards: LabCard[], role = 'MEMBER') {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['cards', 'environmentAction']);
    api.cards.and.returnValue(of(cards));
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1' }, role);
    TestBed.configureTestingModule({
      imports: [LabsPage],
      providers: [
        provideRouter([]),
        { provide: PlayApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: ConfirmService, useValue: { open: () => of('confirm') } },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
        { provide: RoomsStore, useValue: { setCurrentRoom: jasmine.createSpy('setCurrentRoom') } },
      ],
    });
    fixture = TestBed.createComponent(LabsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const labelsIn = (name: string) => [...el().querySelectorAll(`article[aria-label="${name}"] .btn`)].map(b => b.textContent!.trim());

  it('gives members Open lab and Scoreboard only: no Join and no lifecycle controls', async () => {
    await render([card('a', 'SHARED', false), card('b', 'PER_TEAM', false), card('c', 'NONE', false)]);
    for (const name of ['Lab a', 'Lab b', 'Lab c']) {
      expect(labelsIn(name)).toEqual(['Open lab', 'Scoreboard']);
    }
    expect(el().textContent).not.toMatch(/\bJoin\b/);
  });

  it('gives those who run a lab a quick Start for a shared environment and Manage for team copies', async () => {
    await render([card('a', 'SHARED', true), card('b', 'PER_TEAM', true), card('c', 'NONE', true)], 'ADMIN');
    expect(labelsIn('Lab a')).toEqual(['Open lab', 'Scoreboard', 'Start']);
    expect(labelsIn('Lab b')).toEqual(['Open lab', 'Scoreboard', 'Manage']);
    expect(labelsIn('Lab c')).toEqual(['Open lab', 'Scoreboard']);

    api.environmentAction.and.returnValue(of({ successful: true, message: 'Environment started', environment: null }));
    ([...el().querySelectorAll<HTMLButtonElement>('article[aria-label="Lab a"] .btn')].find(b => b.textContent!.includes('Start')))!.click();
    await fixture.whenStable();
    expect(api.environmentAction).toHaveBeenCalledWith('a', 'start', 'shared');
  });
});
