import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SearchService } from '../core/services/search.service';
import { SEARCH_DEBOUNCE_MS } from '../core/stores/search.store';
import { AuthService } from '../services/auth.service';
import { FakeAuthService } from '../testing/fake-auth';
import { GlobalSearchComponent } from './global-search.component';

describe('GlobalSearchComponent', () => {
  let fixture: ComponentFixture<GlobalSearchComponent>;
  let router: Router;
  let api: jasmine.SpyObj<SearchService>;

  beforeEach(() => {
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1', username: 'jmorales' }, 'MEMBER');
    api = jasmine.createSpyObj<SearchService>('SearchService', ['search']);
    api.search.and.returnValue(of({
      query: 'web',
      hits: [
        { type: 'TEAM_LAB', id: 't1', title: 'Web Exploitation 101', subtitle: 'Docker Compose · on dock-01', status: 'ACTIVE' },
        { type: 'CHALLENGE', id: 'c1', title: 'Web login', subtitle: 'Winter Range · Web · 200 pts', roomId: 'r1' },
      ],
    }));
    TestBed.configureTestingModule({
      imports: [GlobalSearchComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: SearchService, useValue: api },
        { provide: SEARCH_DEBOUNCE_MS, useValue: 0 },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(GlobalSearchComponent);
    fixture.detectChanges();
  });

  const input = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
  const options = () => Array.from(document.querySelectorAll<HTMLElement>('.cdk-overlay-container [role="option"]'));
  const key = (name: string, target: EventTarget = input()) =>
    target.dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));

  async function type(text: string) {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
  }

  afterEach(() => fixture.destroy());

  it('focuses on / from anywhere outside a text field', () => {
    key('/', document.body);
    expect(document.activeElement).toBe(input());
  });

  it('shows results grouped by type with the matching text marked', async () => {
    await type('web');
    const labels = Array.from(document.querySelectorAll('.cdk-overlay-container .group-label')).map(el => el.textContent?.trim());
    expect(labels).toEqual(['Team environments', 'Questions']);
    expect(options().length).toBe(2);
    expect(options()[0].querySelector('mark')?.textContent).toBe('Web');
    expect(input().getAttribute('aria-expanded')).toBe('true');
    expect(input().getAttribute('aria-activedescendant')).toBe(options()[0].id);
  });

  it('moves with the arrow keys and opens the active result with Enter', async () => {
    await type('web');
    key('ArrowDown');
    fixture.detectChanges();
    expect(options()[1].getAttribute('aria-selected')).toBe('true');
    key('Enter');
    expect(router.navigate).toHaveBeenCalledWith(['/labs', 'r1'], { queryParams: { challenge: 'c1' }, fragment: undefined });
  });

  it('closes with Escape and clears with a second Escape', async () => {
    await type('web');
    key('Escape');
    fixture.detectChanges();
    expect(options().length).toBe(0);
    key('Escape');
    fixture.detectChanges();
    expect(input().value).toBe('');
  });

  it('says when nothing matches', async () => {
    api.search.and.returnValue(of({ query: 'zzz', hits: [] }));
    await type('zzz');
    expect(document.querySelector('.cdk-overlay-container .results')?.textContent).toContain('No matches for "zzz".');
  });

  it('is an icon that opens a full width panel at narrow widths', async () => {
    fixture.componentRef.setInput('narrow', true);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[aria-label="Search"]') as HTMLButtonElement;
    expect(fixture.nativeElement.querySelector('input')).toBeNull();
    button.click();
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    expect(document.querySelector('.cdk-overlay-container .sheet input')).toBeTruthy();
  });
});
