import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthService } from '../../../services/auth.service';
import { WebsocketService } from '../../../services/websocket.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { PlayApiService } from '../data/play-api.service';
import { ChallengeView, RoomOverview } from '../data/play.models';
import { RoomPlayStore } from './room-play.store';

function challenge(id: string, category: string, solved = false): ChallengeView {
  return {
    id, title: id, category, difficulty: 'EASY', points: 100, author: null, maxAttempts: 3, solves: 0, state: 'OPEN',
    releaseDate: null, expirationDate: null, description: 'Do it', attachments: [], hints: [], attempts: 0, solved, solvedAt: null,
  };
}

describe('RoomPlayStore', () => {
  let api: jasmine.SpyObj<PlayApiService>;
  let socket: Subject<unknown>;
  let overview: RoomOverview;

  beforeEach(() => {
    overview = {
      room: { id: 'r1', name: 'Winter Range', surroundTag: null, creator: null, facilitators: [] },
      playerCount: 2, teamCount: 1, me: { joined: true, points: 0, rank: 1, solved: 1 },
      challenges: [challenge('sqli', 'Web', true), challenge('xss', 'Web'), challenge('rot', 'Crypto')],
    };
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['overview', 'submitFlag', 'unlockHint']);
    api.overview.and.callFake(() => of(overview));
    socket = new Subject();
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1' }, 'MEMBER');
    TestBed.configureTestingModule({
      providers: [
        RoomPlayStore,
        { provide: PlayApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: WebsocketService, useValue: { getMessage: () => socket.asObservable() } },
        { provide: RoomsStore, useValue: { setCurrentRoom: jasmine.createSpy('setCurrentRoom'), join: () => of({}) } },
      ],
    });
  });

  async function loaded() {
    const store = TestBed.inject(RoomPlayStore);
    await store.load('r1');
    return store;
  }

  it('groups challenges by category and filters by status', async () => {
    const store = await loaded();
    expect(store.groups().map(group => [group.name, group.done, group.total])).toEqual([['Web', 1, 2], ['Crypto', 0, 1]]);
    store.setStatus('open');
    expect(store.groups().flatMap(group => group.items.map(item => item.id))).toEqual(['xss', 'rot']);
    store.setCategory('Crypto');
    expect(store.groups().map(group => group.name)).toEqual(['Crypto']);
  });

  it('marks a wrong answer on that challenge and reloads the attempt count', async () => {
    const store = await loaded();
    api.submitFlag.and.returnValue(of({ correct: false, attempts: 1 }));
    const outcome = await store.submit(store.challenges()[1], ' nope ');
    expect(api.submitFlag).toHaveBeenCalledWith('r1', 'xss', 'nope');
    expect(outcome?.correct).toBeFalse();
    expect(store.wrongFor()).toBe('xss');
    expect(api.overview).toHaveBeenCalledTimes(2);
    store.clearAnswerError();
    expect(store.wrongFor()).toBeNull();
  });

  it('shows why a hint could not be unlocked', async () => {
    const store = await loaded();
    api.unlockHint.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Not enough points to use this hint' } })));
    const hint = { id: 'h1', number: 1, cost: 50, unlockAfterAttempts: 0, unlocked: false, text: null, usedAt: null };
    expect(await store.unlockHint(store.challenges()[1], hint)).toBe('Not enough points to use this hint');
  });
});
