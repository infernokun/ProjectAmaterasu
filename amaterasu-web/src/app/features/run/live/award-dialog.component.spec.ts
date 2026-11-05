import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { PlayApiService } from '../../play/data/play-api.service';
import { AwardDialogComponent } from './award-dialog.component';

describe('AwardDialogComponent', () => {
  let fixture: ComponentFixture<AwardDialogComponent>;
  let api: jasmine.SpyObj<PlayApiService>;
  let ref: { close: jasmine.Spy };

  beforeEach(() => {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['awardPoints']);
    api.awardPoints.and.returnValue(of({ points: 150 }));
    ref = { close: jasmine.createSpy('close') };
    TestBed.configureTestingModule({
      imports: [AwardDialogComponent],
      providers: [
        { provide: PlayApiService, useValue: api },
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
});
