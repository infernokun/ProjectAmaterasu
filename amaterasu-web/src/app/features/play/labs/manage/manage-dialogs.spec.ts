import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { LabManageApi } from './lab-manage-api.service';
import { AcceptAnswerDialogComponent, CreditDialogComponent, ResetDialogComponent } from './manage-dialogs';

describe('Lab management dialogs', () => {
  let api: jasmine.SpyObj<LabManageApi>;
  let ref: { close: jasmine.Spy };

  function create<T>(component: Type<T>, data: unknown) {
    api = jasmine.createSpyObj<LabManageApi>('LabManageApi', ['credit', 'acceptAnswer', 'reset']);
    ref = { close: jasmine.createSpy('close') };
    TestBed.configureTestingModule({
      imports: [component],
      providers: [
        { provide: LabManageApi, useValue: api },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return fixture;
  }

  const type = (input: HTMLInputElement, value: string) => {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  it('gives full credit for a near miss with the reason filled in', async () => {
    const fixture = create(CreditDialogComponent, {
      labId: 'r1', teamCredit: 'SHARED_SOLVES', submissionId: 'a1:0', userId: 'u2', username: 'hwalsh', teamName: 'Night Owls',
      questionId: 'q1', questionTitle: 'Find the admin', questionPoints: 50, answer: 'admin',
      nearMiss: { flagId: 'f1', flag: 'Admin', reasons: ['Wrong letter case'] },
    });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('It counts for Night Owls too');
    const reason = [...el.querySelectorAll<HTMLInputElement>('input.input')].pop()!;
    expect(reason.value).toBe('Near miss: wrong letter case');
    api.credit.and.returnValue(of({ data: {} as never, message: 'hwalsh gets 50 pts' }));
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    expect(api.credit).toHaveBeenCalledWith('r1', { submissionId: 'a1:0', points: null, reason: 'Near miss: wrong letter case' });
    await fixture.whenStable();
    expect(ref.close).toHaveBeenCalledWith('hwalsh gets 50 pts');
  });

  it('gives a custom amount and needs a reason', () => {
    const fixture = create(CreditDialogComponent, {
      labId: 'r1', teamCredit: 'SEE_TEAMMATES', userId: 'u2', username: 'hwalsh', teamName: null, questionId: 'q1',
      questionTitle: 'Find the admin', questionPoints: 50,
    });
    const el = fixture.nativeElement as HTMLElement;
    const submit = el.querySelector('button[type=submit]') as HTMLButtonElement;
    expect(submit.disabled).toBeTrue();
    const custom = el.querySelector<HTMLInputElement>('input[type=checkbox]')!;
    custom.checked = true;
    custom.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    type(el.querySelector<HTMLInputElement>('input[type=number]')!, '20');
    type([...el.querySelectorAll<HTMLInputElement>('input.input')].pop()!, 'Half right');
    fixture.detectChanges();
    expect(submit.textContent).toContain('Give 20 pts');
    api.credit.and.returnValue(of({ data: {} as never, message: 'ok' }));
    submit.click();
    expect(api.credit).toHaveBeenCalledWith('r1', { userId: 'u2', questionId: 'q1', points: 20, reason: 'Half right' });
  });

  it('accepts an answer and credits everyone who gave it by default', () => {
    const fixture = create(AcceptAnswerDialogComponent, {
      labId: 'r1', questionId: 'q1', questionTitle: 'Find the admin', answer: ' administrator ', players: 2,
    });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('(2 players)');
    type([...el.querySelectorAll<HTMLInputElement>('input.input')].pop()!, 'Also the account name');
    fixture.detectChanges();
    api.acceptAnswer.and.returnValue(of({ data: {} as never, message: 'Accepted' }));
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    expect(api.acceptAnswer).toHaveBeenCalledWith('r1', {
      questionId: 'q1', answer: 'administrator', caseSensitive: true, creditExisting: true, reason: 'Also the account name',
    });
  });

  it('resets the whole lab only once the name is typed', () => {
    const fixture = create(ResetDialogComponent, { labId: 'r1', userId: 'u2', username: 'hwalsh' });
    const el = fixture.nativeElement as HTMLElement;
    const [reason, name] = [...el.querySelectorAll<HTMLInputElement>('input.input')];
    const submit = el.querySelector('button[type=submit]') as HTMLButtonElement;
    type(reason, 'Start over');
    fixture.detectChanges();
    expect(submit.disabled).toBeTrue();
    type(name, 'hwalsh');
    fixture.detectChanges();
    expect(submit.disabled).toBeFalse();
    api.reset.and.returnValue(of({ data: {} as never, message: 'Reset' }));
    submit.click();
    expect(api.reset).toHaveBeenCalledWith('r1', { userId: 'u2', questionId: null, reason: 'Start over' });
  });

  it('resets one question without typing the name', () => {
    const fixture = create(ResetDialogComponent, {
      labId: 'r1', userId: 'u2', username: 'hwalsh', questionId: 'q1', questionTitle: 'Find the admin', pointsChange: 10,
    });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('(+10 pts)');
    type(el.querySelector<HTMLInputElement>('input.input')!, 'Hint was wrong');
    fixture.detectChanges();
    expect((el.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBeFalse();
  });
});
