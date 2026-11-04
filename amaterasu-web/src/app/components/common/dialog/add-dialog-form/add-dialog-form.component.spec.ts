import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AddDialogFormComponent } from './add-dialog-form.component';
import { SimpleFormData, TextQuestion } from '../../../../models/simple-form-data.model';

describe('AddDialogFormComponent', () => {
  let component: AddDialogFormComponent;
  let fixture: ComponentFixture<AddDialogFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddDialogFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: new SimpleFormData('test', [new TextQuestion({ key: 'name', label: 'Name' })]) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AddDialogFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
