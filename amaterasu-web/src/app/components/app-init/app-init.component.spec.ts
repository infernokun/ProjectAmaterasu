import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppInitComponent } from './app-init.component';

describe('AppInitComponent', () => {
  let component: AppInitComponent;
  let fixture: ComponentFixture<AppInitComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppInitComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AppInitComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
