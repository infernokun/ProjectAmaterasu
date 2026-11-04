import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('shows the classification banner above and below the page', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const banners = (fixture.nativeElement as HTMLElement).querySelectorAll('.classification');
    expect(banners.length).toBe(2);
    expect(banners[0].textContent).toContain('UNCLASSIFIED');
  });

  it('shows a loading state until the first page is ready', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.loading-text')?.textContent).toContain('Loading');
  });
});
