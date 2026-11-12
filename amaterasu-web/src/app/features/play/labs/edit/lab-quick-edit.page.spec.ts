import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RoomsStore } from '../../../../core/stores/rooms.store';
import { DifficultyLevel } from '../../../../enums/difficulty-level.enum';
import { AuthService } from '../../../../services/auth.service';
import { ConfirmService } from '../../../../shared/services/confirm.service';
import { FakeAuthService } from '../../../../testing/fake-auth';
import { AuthorChallenge, AuthoringService } from '../../../build/rooms/authoring.service';
import { RoomPlayStore } from '../../rooms/room-play.store';
import { LabQuickEditPage } from './lab-quick-edit.page';

const challenge = (id: string, patch: Partial<AuthorChallenge> = {}): AuthorChallenge => ({
  id, roomId: 'r1', labId: null, question: id, category: 'Web', difficultyLevel: DifficultyLevel.EASY, points: 100, maxAttempts: null,
  description: 'Look closer', visible: true, releaseDate: '2026-11-04 09:00:00', expirationDate: null, solutionExplanation: '', author: null,
  flags: [{ flag: 'f', caseSensitive: true, surroundWithTag: true, weight: 1 }], hints: [{ hint: 'Logs', cost: 25, unlockAfterAttempts: 0 }],
  solveCount: 0, attemptCount: 0, attachments: [], ...patch,
});

describe('LabQuickEditPage', () => {
  let fixture: ComponentFixture<LabQuickEditPage>;
  let api: jasmine.SpyObj<AuthoringService>;
  let lab: { selected: () => { id: string }; reload: jasmine.Spy };
  let confirm: jasmine.Spy;
  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => [...el().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent!.trim() === label)!;
  const field = (label: string) => [...el().querySelectorAll('label.field')]
    .find(f => f.querySelector('.label')?.textContent!.trim() === label)?.querySelector<HTMLInputElement>('input, textarea');

  beforeEach(async () => {
    const auth = new FakeAuthService();
    auth.signIn({ id: 'c1', username: 'aokafor' }, 'CREATOR');
    api = jasmine.createSpyObj<AuthoringService>('AuthoringService', [
      'room', 'challenges', 'players', 'facilitators', 'facilitatorCandidates', 'labs', 'files', 'update', 'updateRoom',
    ]);
    const room = { id: 'r1', name: 'On Call', surroundTag: 'AMA{x}', updatedAt: null, scenario: 'The shop is down.',
      environmentMode: 'NONE' as const, environmentId: null, teamCredit: 'SEE_TEAMMATES' as const, published: true };
    api.room.and.returnValue(of(room));
    api.challenges.and.returnValue(of([challenge('Find the log'), challenge('Read the dump', { points: 200 })]));
    api.players.and.returnValue(of(3));
    api.facilitators.and.returnValue(of([]));
    api.facilitatorCandidates.and.returnValue(of([]));
    api.labs.and.returnValue(of([]));
    api.files.and.returnValue(of([]));
    api.update.and.callFake(saved => of(saved));
    api.updateRoom.and.callFake((_id, changes) => of({ ...room, ...changes }));
    lab = { selected: () => ({ id: 'Read the dump' }), reload: jasmine.createSpy('reload').and.resolveTo() };
    confirm = jasmine.createSpy('open').and.returnValue(of('cancel'));
    TestBed.configureTestingModule({
      imports: [LabQuickEditPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AuthoringService, useValue: api },
        { provide: RoomsStore, useValue: { load: jasmine.createSpy('load') } },
        { provide: RoomPlayStore, useValue: lab },
        { provide: ConfirmService, useValue: { open: confirm } },
      ],
    });
    fixture = TestBed.createComponent(LabQuickEditPage);
    fixture.componentRef.setInput('labId', 'r1');
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
  });

  it('starts on the question open on the lab page, with its text, points, flags and hints', () => {
    expect(field('Title')!.value).toBe('Read the dump');
    expect(field('Points')!.value).toBe('200');
    expect(field('Description')!.value).toBe('Look closer');
    expect(el().querySelector<HTMLInputElement>('[aria-label="Flag value"]')!.value).toBe('f');
    expect(el().querySelector<HTMLInputElement>('[aria-label="Hint text"]')!.value).toBe('Logs');
    expect(el().querySelector<HTMLSelectElement>('.q-pick select')!.value).toBe('Read the dump');
  });

  it('leaves the schedule, files and solution notes to the full editor, which it links to', () => {
    expect(field('Opens')).toBeUndefined();
    expect(field('Solution notes')).toBeUndefined();
    const full = [...el().querySelectorAll<HTMLAnchorElement>('a')].find(a => a.textContent!.includes('Open full editor'))!;
    expect(full.getAttribute('href')).toBe('/build/labs/r1?from=lab&step=questions');
  });

  it('saves the question and refreshes the lab around it', async () => {
    const points = field('Points')!;
    points.value = '300';
    points.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button('Save question').click();
    await fixture.whenStable();
    expect(api.update).toHaveBeenCalledWith(jasmine.objectContaining({ id: 'Read the dump', points: 300 }));
    expect(lab.reload).toHaveBeenCalled();
  });

  it('saves the scenario on its own', async () => {
    expect(button('Save scenario').disabled).toBeTrue();
    const scenario = el().querySelector<HTMLTextAreaElement>('app-scenario-field textarea')!;
    scenario.value = 'The shop is down again.';
    scenario.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button('Save scenario').click();
    await fixture.whenStable();
    expect(api.updateRoom).toHaveBeenCalledWith('r1', { scenario: 'The shop is down again.' });
    expect(lab.reload).toHaveBeenCalled();
  });

  it('asks before leaving or switching with unsaved edits', async () => {
    expect(fixture.componentInstance.canLeave()).toBeTrue();
    const title = field('Title')!;
    title.value = 'Read the core dump';
    title.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(await fixture.componentInstance.canLeave()).toBeFalse();

    const select = el().querySelector<HTMLSelectElement>('.q-pick select')!;
    select.value = 'Find the log';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(select.value).toBe('Read the dump');
    expect(field('Title')!.value).toBe('Read the core dump');
  });
});
