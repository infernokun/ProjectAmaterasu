import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { PlayApiService } from '../../play/data/play-api.service';
import { LabManageApi } from '../../play/labs/manage/lab-manage-api.service';
import { AwardDialogComponent } from './award-dialog.component';

describe('AwardDialogComponent', () => {
  let fixture: ComponentFixture<AwardDialogComponent>;
  let api: jasmine.SpyObj<PlayApiService>;
  let ref: { close: jasmine.Spy };
  let manage: jasmine.SpyObj<LabManageApi>;

  beforeEach(() => {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['awardPoints']);
    api.awardPoints.and.returnValue(of({ points: 150 }));
    ref = { close: jasmine.createSpy('close') };
    manage = jasmine.createSpyObj<LabManageApi>('LabManageApi', ['award']);
    TestBed.configureTestingModule({
      imports: [AwardDialogComponent],
      providers: [
        { provide: PlayApiService, useValue: api },
        { provide: LabManageApi, useValue: manage },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: { roomId: 'r1', roomName: 'Winter Range', players: [{ userId: 'u2', username: 'hwalsh', teamName: 'Night Owls', points: 100 }] } },
      ],
    });
    fixture = TestBed.createComponent(AwardDialogComponent);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const submitButton = () => el().querySelector<HTMLButtonElement>('button[type=submit]')!;

  it('needs a reason before awarding', () => {
    expect(submitButton().disabled).toBeTrue();
    expect(el().textContent).toContain('hwalsh gets +50 pts in Winter Range, going to 150.');
  });

  it('awards the amount with the reason', () => {
    const reason = el().querySelectorAll<HTMLInputElement>('input')[1];
    reason.value = 'Writeup bonus';
    reason.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    submitButton().click();
    expect(api.awardPoints).toHaveBeenCalledWith('r1', 'u2', 50, 'Writeup bonus');
  });

  it('awards a team as a whole when teams are offered', () => {
    manage.award.and.returnValue(of({ data: null, message: '+50 pts to Night Owls' }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AwardDialogComponent],
      providers: [
        { provide: PlayApiService, useValue: api },
        { provide: LabManageApi, useValue: manage },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: { roomId: 'r1', roomName: 'Winter Range', teamId: 't1',
          players: [{ userId: 'u2', username: 'hwalsh', teamName: 'Night Owls', points: 100 }],
          teams: [{ teamId: 't1', name: 'Night Owls', points: 300 }] } },
      ],
    });
    fixture = TestBed.createComponent(AwardDialogComponent);
    fixture.detectChanges();
    expect(el().textContent).toContain('Night Owls gets +50 pts in Winter Range, going to 350.');
    const reason = [...el().querySelectorAll<HTMLInputElement>('input')].pop()!;
    reason.value = 'Helped the Larks';
    reason.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    submitButton().click();
    expect(manage.award).toHaveBeenCalledWith('r1', { teamId: 't1', points: 50, reason: 'Helped the Larks' });
    expect(api.awardPoints).not.toHaveBeenCalled();
  });
});
