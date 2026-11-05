import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Component } from '@angular/core';
import { CrumbsService } from './crumbs.service';

@Component({ template: '' })
class BlankComponent {}

describe('CrumbsService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'a', component: BlankComponent }, { path: 'b', component: BlankComponent }])],
    });
  });

  it('keeps a page breadcrumb across query changes and clears it on a new page', async () => {
    const router = TestBed.inject(Router);
    const crumbs = TestBed.inject(CrumbsService);
    await router.navigateByUrl('/a');
    crumbs.set([{ label: 'Rooms' }, { label: 'Winter Range' }]);

    await router.navigateByUrl('/a?challenge=x');
    expect(crumbs.custom()?.length).toBe(2);

    await router.navigateByUrl('/b');
    expect(crumbs.custom()).toBeNull();
  });
});
