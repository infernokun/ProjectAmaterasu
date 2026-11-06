import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AppComponent } from '../../app.component';
import { classificationOf } from '../../core/classification';
import { AppInfoStore } from '../../core/stores/app-info.store';
import { ApplicationInfo } from '../../models/application-info.model';
import { AppInitService } from '../../services/app-init.service';
import { AuthService } from '../../services/auth.service';
import { EnvironmentService } from '../../services/environment.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { provideRouter } from '@angular/router';
import { ClassificationSettingComponent } from './classification-setting.component';

describe('classification banner', () => {
  let auth: FakeAuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    auth = new FakeAuthService();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: EnvironmentService, useValue: { settings: { restUrl: '/api' } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const setInfo = (settings: object) =>
    TestBed.inject(AppInitService).applicationInfo$.next(new ApplicationInfo({ name: 'Amaterasu', settings: JSON.stringify(settings) }));

  it('falls back to UNCLASSIFIED for missing or unknown values', () => {
    expect(classificationOf(undefined).label).toBe('UNCLASSIFIED');
    expect(classificationOf('BOGUS').label).toBe('UNCLASSIFIED');
    expect(classificationOf('TOP_SECRET_SCI')).toEqual(jasmine.objectContaining({ label: 'TOP SECRET//SCI', background: '#fce83a' }));
  });

  it('shows the marking from the app settings above and below the page, or none at all', () => {
    const fixture = TestBed.createComponent(AppComponent);
    setInfo({ classification: 'SECRET' });
    fixture.detectChanges();
    const banners = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.classification');
    expect(banners.length).toBe(2);
    expect(banners[0].textContent!.trim()).toBe('SECRET');
    expect(banners[1].style.background).toContain('rgb(200, 16, 46)');

    setInfo({ classification: 'NONE' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.classification').length).toBe(0);
  });

  describe('setting', () => {
    let fixture: ComponentFixture<ClassificationSettingComponent>;
    const el = () => fixture.nativeElement as HTMLElement;

    it('tells members what their admin set', () => {
      auth.signIn({ id: 'u2', username: 'jmorales' }, 'MEMBER');
      setInfo({ classification: 'CUI' });
      fixture = TestBed.createComponent(ClassificationSettingComponent);
      fixture.detectChanges();
      expect(el().querySelector('.tag')!.textContent).toContain('Set by your admin: CUI');
      expect(el().querySelectorAll('button').length).toBe(0);
    });

    it('lets admins pick a marking and saves it for everyone', async () => {
      auth.signIn({ id: 'u1', username: 'sbell' }, 'ADMIN');
      setInfo({ classification: 'UNCLASSIFIED', other: 1 });
      fixture = TestBed.createComponent(ClassificationSettingComponent);
      fixture.detectChanges();
      const options = [...el().querySelectorAll<HTMLButtonElement>('button[role=radio]')];
      expect(options.map(o => o.textContent!.replace(/\s+/g, ' ').trim())).toEqual([
        'Aa UNCLASSIFIED', 'Aa CUI', 'Aa CONFIDENTIAL', 'Aa SECRET', 'Aa TOP SECRET', 'Aa TOP SECRET//SCI', 'No banner',
      ]);
      expect(options[0].getAttribute('aria-checked')).toBe('true');

      options[3].click();
      const request = http.expectOne('/api/application-info/classification');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ classification: 'SECRET' });
      request.flush({ code: 200, message: 'ok', data: { name: 'Amaterasu', settings: '{"classification":"SECRET","other":1}' } });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(TestBed.inject(AppInfoStore).classification().label).toBe('SECRET');
      expect(el().querySelectorAll('button[role=radio]')[3].getAttribute('aria-checked')).toBe('true');
    });
  });
});
