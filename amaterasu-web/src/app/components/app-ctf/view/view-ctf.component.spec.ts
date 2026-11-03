import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CTFEntity } from '../../../models/ctf/ctf-entity.model';
import { JoinRoomResponse } from '../../../models/dto/join-room-response.model';
import { RoomService } from '../../../services/ctf/room.service';
import { ViewCTFComponent } from './view-ctf.component';

describe('ViewCTFComponent', () => {
  let component: ViewCTFComponent;
  let fixture: ComponentFixture<ViewCTFComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewCTFComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: new CTFEntity({ question: 'Question', maxAttempts: 3, points: 100 }) }
      ]
    }).compileComponents();

    TestBed.inject(RoomService).setCurrentRoomUser(new JoinRoomResponse({ userId: 'user-1', roomId: 'room-1', points: 0 }));

    fixture = TestBed.createComponent(ViewCTFComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
