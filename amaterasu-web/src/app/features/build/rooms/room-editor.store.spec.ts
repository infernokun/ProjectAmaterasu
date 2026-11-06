import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthorChallenge, AuthoringService } from './authoring.service';
import { NEW_ID, RoomEditorStore, draftProblem, toDraft } from './room-editor.store';
import { wrapperParts } from './room-editor.page';

const challenge = (id: string, patch: Partial<AuthorChallenge> = {}): AuthorChallenge => ({
  id, roomId: 'r1', question: id, category: 'Web', difficultyLevel: DifficultyLevel.EASY, points: 100, maxAttempts: 5,
  description: '', visible: true, releaseDate: '2026-11-04 09:00:00', expirationDate: null, solutionExplanation: '', author: null,
  flags: [{ flag: 'f', caseSensitive: true, surroundWithTag: true, weight: 1 }], hints: [], solveCount: 0, attemptCount: 0, ...patch,
});

describe('RoomEditorStore', () => {
  let api: jasmine.SpyObj<AuthoringService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<AuthoringService>('AuthoringService', [
      'room', 'challenges', 'players', 'create', 'update', 'delete', 'updateRoom', 'deleteRoom', 'importChallenges',
    ]);
    api.room.and.returnValue(of({ id: 'r1', name: 'Winter Range 2026', surroundTag: 'AMA{x}', updatedAt: null }));
    api.challenges.and.returnValue(of([
      challenge('Union Based Dump', { points: 300, hints: [{ id: 'h1', hint: 'Count the columns', cost: 25, unlockAfterAttempts: 0 }] }),
      challenge('Kerberoast', { category: 'Active Directory', points: 300 }),
      challenge('SQLi Login Bypass', { points: 200 }),
    ]));
    api.players.and.returnValue(of(38));
    TestBed.configureTestingModule({
      providers: [RoomEditorStore, { provide: AuthoringService, useValue: api }, { provide: RoomsStore, useValue: { load: jasmine.createSpy('load') } }],
    });
  });

  async function loaded() {
    const store = TestBed.inject(RoomEditorStore);
    await store.init('r1');
    return store;
  }

  it('loads the room with challenges grouped by category and points', async () => {
    const store = await loaded();
    expect(store.challenges().map(c => c.id)).toEqual(['Kerberoast', 'SQLi Login Bypass', 'Union Based Dump']);
    expect(store.selectedId()).toBe('Kerberoast');
    expect(store.totalPoints()).toBe(800);
    expect(store.categories()).toEqual(['Active Directory', 'Web']);
    await Promise.resolve();
    expect(store.players()).toBe(38);
  });

  it('edits a challenge and saves dates in UTC with hint ids kept', async () => {
    const store = await loaded();
    store.select('Union Based Dump');
    expect(store.dirty()).toBeFalse();
    store.patch({ points: 350, closes: '' });
    store.patchHint(0, { cost: 30 });
    store.addHint();
    store.patchHint(1, { hint: 'Look at information_schema', cost: 50, unlockAfterAttempts: 3 });
    expect(store.dirty()).toBeTrue();

    api.update.and.callFake(c => of({ ...c, id: 'Union Based Dump' } as AuthorChallenge));
    await store.save();
    const sent = api.update.calls.mostRecent().args[0];
    expect(sent.points).toBe(350);
    expect(sent.releaseDate).toBe('2026-11-04 09:00:00');
    expect(sent.expirationDate).toBeNull();
    expect(sent.hints).toEqual([
      { id: 'h1', hint: 'Count the columns', cost: 30, unlockAfterAttempts: 0 },
      { hint: 'Look at information_schema', cost: 50, unlockAfterAttempts: 3 },
    ]);
    expect(store.dirty()).toBeFalse();
  });

  it('creates new challenges in the room', async () => {
    const store = await loaded();
    store.newChallenge('aokafor');
    expect(store.selectedId()).toBe(NEW_ID);
    expect(store.problem()).toBe('Give the challenge a title.');
    store.patch({ question: 'Export Endpoint' });
    expect(store.problem()).toBe('Add at least one flag players can submit.');
    store.patchFlag(0, { flag: 'exp0rt_all' });
    expect(store.problem()).toBeNull();

    api.create.and.callFake(c => of({ ...c, id: 'new-id' } as AuthorChallenge));
    await store.save();
    const sent = api.create.calls.mostRecent().args[0];
    expect(sent.roomId).toBe('r1');
    expect(sent.author).toBe('aokafor');
    expect(store.selectedId()).toBe('new-id');
    expect(store.challenges().length).toBe(4);
  });

  it('deletes the selected challenge and imports exported files', async () => {
    const store = await loaded();
    api.delete.and.returnValue(of({}));
    await store.deleteSelected();
    expect(api.delete).toHaveBeenCalledWith('Kerberoast');
    expect(store.selectedId()).toBe('SQLi Login Bypass');

    const exported = store.exportJson();
    api.importChallenges.and.returnValue(of(2));
    expect(await store.importFile(exported)).toBe(2);
    expect(api.importChallenges.calls.mostRecent().args[0].length).toBe(2);
    await expectAsync(store.importFile('not json')).toBeRejectedWithError('The file is not valid JSON.');
  });
});

describe('challenge drafts', () => {
  it('needs closing after opening', () => {
    const draft = { ...toDraft(challenge('x')), opens: '2026-11-05T10:00', closes: '2026-11-05T09:00' };
    expect(draftProblem(draft)).toBe('It has to close after it opens.');
  });

  it('splits flag wrappers around the placeholder', () => {
    expect(wrapperParts('CTF{x}')).toEqual({ prefix: 'CTF{', suffix: '}', name: 'CTF' });
    expect(wrapperParts('docker{?}')).toEqual({ prefix: 'docker{', suffix: '}', name: 'docker' });
    expect(wrapperParts('AMA')).toEqual({ prefix: 'AMA{', suffix: '}', name: 'AMA' });
  });
});
