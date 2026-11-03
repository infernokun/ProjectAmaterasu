import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CTFMainComponent } from './ctf-main.component';

describe('CtfMainComponent', () => {
  let component: CTFMainComponent;
  let fixture: ComponentFixture<CTFMainComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CTFMainComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CTFMainComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
