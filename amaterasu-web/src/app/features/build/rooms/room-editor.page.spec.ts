import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthService } from '../../../services/auth.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { AuthorChallenge, AuthoringService } from './authoring.service';
import { RoomEditorPage } from './room-editor.page';

const challenge = (id: string, patch: Partial<AuthorChallenge> = {}): AuthorChallenge => ({
  id, roomId: 'r1', labId: null, question: id, category: 'Web', difficultyLevel: DifficultyLevel.EASY, points: 100, maxAttempts: 5,
  description: '', visible: true, releaseDate: '2026-11-04 09:00:00', expirationDate: null, solutionExplanation: '', author: null,
  flags: [{ flag: 'f', caseSensitive: true, surroundWithTag: true, weight: 1 }], hints: [], solveCount: 0, attemptCount: 0, attachments: [], ...patch,
});

describe('RoomEditorPage', () => {
  let fixture: ComponentFixture<RoomEditorPage>;
  let api: jasmine.SpyObj<AuthoringService>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    const auth = new FakeAuthService();
    auth.signIn({ id: 'c1', username: 'aokafor' }, 'CREATOR');
    api = jasmine.createSpyObj<AuthoringService>('AuthoringService', [
      'room', 'challenges', 'players', 'facilitators', 'facilitatorCandidates', 'setFacilitators', 'reorder', 'labs', 'update', 'files',
      'updateRoom',
    ]);
    api.files.and.returnValue(of([]));
    api.room.and.returnValue(of({ id: 'r1', name: 'Winter Range 2026', surroundTag: 'AMA{x}', updatedAt: null,
      scenario: '## Situation\nA quiet range.', environmentMode: 'NONE' as const, environmentId: null }));
    api.challenges.and.returnValue(of([challenge('SQLi Login Bypass', { labId: 'lab1' }), challenge('Kerberoast'), challenge('Memory Lane')]));
    api.players.and.returnValue(of(38));
    api.facilitators.and.returnValue(of([{ id: 'u1', username: 'dkim' }]));
    api.facilitatorCandidates.and.returnValue(of([{ id: 'u1', username: 'dkim' }, { id: 'u2', username: 'rhale' }]));
    api.labs.and.returnValue(of([{ id: 'lab1', name: 'Web Exploitation 101', published: true }, { id: 'lab2', name: 'AD Forest', published: false }]));
    api.reorder.and.returnValue(of({}));
    TestBed.configureTestingModule({
      imports: [RoomEditorPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AuthoringService, useValue: api },
        { provide: RoomsStore, useValue: { load: jasmine.createSpy('load') } },
      ],
    });
    fixture = TestBed.createComponent(RoomEditorPage);
    fixture.componentRef.setInput('roomId', 'r1');
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
  });

  it('lists the room facilitators and removes one', async () => {
    const tags = el().querySelectorAll('.tag.fac');
    expect([...tags].map(t => t.textContent!.trim())).toEqual(['dkim']);

    api.setFacilitators.and.returnValue(of([]));
    el().querySelector<HTMLButtonElement>('[aria-label="Remove dkim"]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.setFacilitators).toHaveBeenCalledWith('r1', []);
    expect(el().querySelectorAll('.tag.fac').length).toBe(0);
  });

  it('shows challenges in their order with a grip that moves them with the arrow keys', async () => {
    const titles = () => [...el().querySelectorAll('.drop-list .title')].map(t => t.textContent!.trim());
    expect(titles()).toEqual(['SQLi Login Bypass', 'Kerberoast', 'Memory Lane']);
    expect(el().textContent).toContain('Drag to change the order players see.');

    const grip = el().querySelectorAll<HTMLButtonElement>('.grip')[0];
    expect(grip.getAttribute('aria-label')).toBe('Move SQLi Login Bypass, position 1 of 3');
    grip.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.reorder).toHaveBeenCalledWith('r1', ['Kerberoast', 'SQLi Login Bypass', 'Memory Lane']);
    expect(titles()).toEqual(['Kerberoast', 'SQLi Login Bypass', 'Memory Lane']);
  });

  it('picks the environment the lab runs, marking drafts, and saves it with the scenario', async () => {
    const shared = [...el().querySelectorAll<HTMLButtonElement>('.env-field .seg button')].find(b => b.textContent!.trim() === 'Shared')!;
    shared.click();
    fixture.detectChanges();
    const select = el().querySelector<HTMLSelectElement>('.env-pick')!;
    expect([...select.options].map(o => o.textContent!.trim())).toEqual(['Pick an environment', 'Web Exploitation 101', 'AD Forest (draft)']);
    select.value = 'lab1';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    api.updateRoom.and.returnValue(of({ id: 'r1', name: 'Winter Range 2026', surroundTag: 'AMA{x}', updatedAt: null,
      scenario: '## Situation\nA quiet range.', environmentMode: 'SHARED' as const, environmentId: 'lab1' }));
    [...el().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent!.trim() === 'Save lab')!.click();
    await fixture.whenStable();
    expect(api.updateRoom).toHaveBeenCalledWith('r1', jasmine.objectContaining({
      environmentMode: 'SHARED', environmentId: 'lab1', scenario: '## Situation\nA quiet range.',
    }));
  });

  it('treats an empty attempts box as unlimited', () => {
    const input = [...el().querySelectorAll('label.field')]
      .find(field => field.querySelector('.label')!.textContent!.trim() === 'Attempts allowed')!.querySelector('input')!;
    input.value = '';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(input.placeholder).toBe('Unlimited');
    expect(el().textContent).not.toContain('Allow at least one attempt');
  });
});
