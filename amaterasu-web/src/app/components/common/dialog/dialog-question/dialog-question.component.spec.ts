import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogQuestionComponent } from './dialog-question.component';
import { TextQuestion } from '../../../../models/simple-form-data.model';

describe('DialogQuestionComponent', () => {
  let component: DialogQuestionComponent;
  let fixture: ComponentFixture<DialogQuestionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DialogQuestionComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(DialogQuestionComponent);
    component = fixture.componentInstance;
    component.question = new TextQuestion({ key: 'name', label: 'Name' });
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
