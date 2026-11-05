import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { AppInitService } from '../../services/app-init.service';
import { InitializationComponent } from './initialization.component';

describe('InitializationComponent', () => {
  let fixture: ComponentFixture<InitializationComponent>;
  let init: { isInitialized: () => unknown; createApplicationInfo: jasmine.Spy; setInitialized: jasmine.Spy; applicationInfo$: BehaviorSubject<unknown> };

  beforeEach(async () => {
    init = {
      isInitialized: () => of(false),
      createApplicationInfo: jasmine.createSpy('createApplicationInfo').and.returnValue(of({ data: { name: 'Winter Range 2026' } })),
      setInitialized: jasmine.createSpy('setInitialized'),
      applicationInfo$: new BehaviorSubject<unknown>(null),
    };
    await TestBed.configureTestingModule({
      imports: [InitializationComponent],
      providers: [provideRouter([]), { provide: AppInitService, useValue: init }],
    }).compileComponents();
    fixture = TestBed.createComponent(InitializationComponent);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  it('asks for a name before saving', () => {
    el().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    expect(init.createApplicationInfo).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Give the installation a name');
  });

  it('saves the name and description', () => {
    const [name, description] = Array.from(el().querySelectorAll<HTMLInputElement>('input'));
    name.value = 'Winter Range 2026';
    name.dispatchEvent(new Event('input'));
    description.value = 'Labs for the winter cohort';
    description.dispatchEvent(new Event('input'));
    el().querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const saved = init.createApplicationInfo.calls.mostRecent().args[0];
    expect(saved.name).toBe('Winter Range 2026');
    expect(saved.description).toBe('Labs for the winter cohort');
    expect(init.setInitialized).toHaveBeenCalledWith(true);
  });
});
