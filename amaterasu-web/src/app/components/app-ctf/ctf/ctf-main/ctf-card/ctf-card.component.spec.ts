import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CTFCardComponent } from './ctf-card.component';

describe('CTFCardComponent', () => {
  let component: CTFCardComponent;
  let fixture: ComponentFixture<CTFCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CTFCardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CTFCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
