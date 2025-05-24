import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CTFHomeComponent } from './home.component';

describe('CTFHomeComponent', () => {
  let component: CTFHomeComponent;
  let fixture: ComponentFixture<CTFHomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [CTFHomeComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(CTFHomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
