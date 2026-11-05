import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../services/auth.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { DEVICE_THEME_KEY, ThemeStore, userThemeKey } from './theme.store';

describe('ThemeStore', () => {
  let auth: FakeAuthService;
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.removeItem(DEVICE_THEME_KEY);
    localStorage.removeItem(userThemeKey('u1'));
    root.removeAttribute('data-theme');
    auth = new FakeAuthService();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
  });

  afterEach(() => {
    root.removeAttribute('data-theme');
    localStorage.removeItem(DEVICE_THEME_KEY);
    localStorage.removeItem(userThemeKey('u1'));
  });

  it('matches the system until a theme is picked', () => {
    const store = TestBed.inject(ThemeStore);
    TestBed.tick();
    expect(store.preference()).toBe('system');
    expect(root.hasAttribute('data-theme')).toBeFalse();
  });

  it('applies and saves a picked theme for the device and the user', () => {
    auth.signIn({ id: 'u1' }, 'MEMBER');
    const store = TestBed.inject(ThemeStore);
    store.setPreference('light');
    TestBed.tick();

    expect(store.theme()).toBe('light');
    expect(root.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem(DEVICE_THEME_KEY)).toBe('light');
    expect(localStorage.getItem(userThemeKey('u1'))).toBe('light');
  });

  it("switches to the user's own choice when they sign in", () => {
    localStorage.setItem(DEVICE_THEME_KEY, 'light');
    localStorage.setItem(userThemeKey('u1'), 'dark');
    const store = TestBed.inject(ThemeStore);
    TestBed.tick();
    expect(store.preference()).toBe('light');

    auth.signIn({ id: 'u1' }, 'MEMBER');
    TestBed.tick();
    expect(store.preference()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');
  });

  it('keeps working when storage throws', () => {
    spyOn(Storage.prototype, 'setItem').and.throwError('blocked');
    const store = TestBed.inject(ThemeStore);
    expect(() => store.setPreference('dark')).not.toThrow();
    TestBed.tick();
    expect(root.getAttribute('data-theme')).toBe('dark');
  });
});
