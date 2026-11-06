import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../services/auth.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { DEVICE_DENSITY_KEY, DensityStore, userDensityKey } from './density.store';

describe('DensityStore', () => {
  let auth: FakeAuthService;
  const root = document.documentElement;

  const clear = () => {
    localStorage.removeItem(DEVICE_DENSITY_KEY);
    localStorage.removeItem(userDensityKey('u1'));
    root.removeAttribute('data-density');
  };

  beforeEach(() => {
    clear();
    auth = new FakeAuthService();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
  });

  afterEach(clear);

  it('is comfortable until compact is picked', () => {
    const store = TestBed.inject(DensityStore);
    TestBed.tick();
    expect(store.density()).toBe('comfortable');
    expect(root.hasAttribute('data-density')).toBeFalse();
  });

  it('applies compact through the tokens and saves it for the device and the user', () => {
    auth.signIn({ id: 'u1' }, 'MEMBER');
    const store = TestBed.inject(DensityStore);
    store.setDensity('compact');
    TestBed.tick();

    expect(root.getAttribute('data-density')).toBe('compact');
    expect(getComputedStyle(root).getPropertyValue('--control-h').trim()).toBe('32px');
    expect(localStorage.getItem(DEVICE_DENSITY_KEY)).toBe('compact');
    expect(localStorage.getItem(userDensityKey('u1'))).toBe('compact');

    store.setDensity('comfortable');
    TestBed.tick();
    expect(root.hasAttribute('data-density')).toBeFalse();
    expect(getComputedStyle(root).getPropertyValue('--control-h').trim()).toBe('38px');
  });

  it("switches to the user's own choice when they sign in", () => {
    localStorage.setItem(userDensityKey('u1'), 'compact');
    const store = TestBed.inject(DensityStore);
    TestBed.tick();
    expect(store.density()).toBe('comfortable');

    auth.signIn({ id: 'u1' }, 'MEMBER');
    TestBed.tick();
    expect(store.density()).toBe('compact');
    expect(localStorage.getItem(DEVICE_DENSITY_KEY)).toBe('compact');
  });
});
