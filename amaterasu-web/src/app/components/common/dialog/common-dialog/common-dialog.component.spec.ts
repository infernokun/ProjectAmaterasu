import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CommonDialogComponent } from './common-dialog.component';

describe('CommonDialogComponent', () => {
  let component: CommonDialogComponent;
  let fixture: ComponentFixture<CommonDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommonDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: { title: 'Title', isCode: false, content: 'Content', fileType: 'txt', isReadOnly: true } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CommonDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
