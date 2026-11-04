import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LabSettingsComponent } from './lab-settings.component';

describe('LabSettingsComponent', () => {
  let component: LabSettingsComponent;
  let fixture: ComponentFixture<LabSettingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LabSettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LabSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
