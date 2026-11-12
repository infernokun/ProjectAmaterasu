import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Route, Router, RouterOutlet, Routes, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { RoomsStore } from '../../core/stores/rooms.store';
import { LabTracker } from '../../models/lab/lab-tracker.model';
import { AuthService } from '../../services/auth.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { PlayApiService } from './data/play-api.service';
import { PLAY_ROUTES } from './play.routes';

/** Stands in for every page, so the test is about the addresses alone. */
@Component({ template: '<router-outlet />', imports: [RouterOutlet] })
class StubPage {
  readonly path = inject(ActivatedRoute).snapshot.routeConfig?.path;
}

function stubbed(routes: Routes): Routes {
  return routes.map(route => {
    const copy: Route = { ...route };
    if (copy.loadComponent) {
      delete copy.loadComponent;
      copy.component = StubPage;
    }
    if (copy.children) copy.children = stubbed(copy.children);
    return copy;
  });
}

describe('Lab addresses', () => {
  let harness: RouterTestingHarness;
  let auth: FakeAuthService;
  const trackers: Record<string, object> = {
    shared: { id: 'shared', roomId: 'r1', labStarted: { id: 'env1' } },
    team: { id: 'team', labStarted: { id: 'env2' } },
    orphan: { id: 'orphan', labStarted: { id: 'env9' } },
  };

  async function setUp(roles = 'MEMBER') {
    auth = new FakeAuthService();
    auth.signIn({ id: 'u1' }, roles);
    TestBed.configureTestingModule({
      providers: [
        provideRouter(stubbed(PLAY_ROUTES), withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
        { provide: AuthService, useValue: auth },
        { provide: RoomsStore, useValue: { currentRoom: () => undefined } },
        {
          provide: PlayApiService, useValue: {
            tracker: (id: string) => (trackers[id] ? of(new LabTracker(trackers[id])) : throwError(() => new Error('404'))),
            cards: () => of([{ id: 'r2', environment: { labId: 'env2', trackerId: 'team' } }]),
          },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
  }

  const url = () => TestBed.inject(Router).url;
  const page = () => harness.routeNativeElement && TestBed.inject(Router).routerState.snapshot.root;
  const deepest = () => {
    let route = page()!;
    while (route.firstChild) route = route.firstChild;
    return route;
  };

  describe('old copy addresses', () => {
    beforeEach(() => setUp());

    it('send a shared copy to its lab\'s details', async () => {
      await harness.navigateByUrl('/environments/shared');
      expect(url()).toBe('/labs/r1/details?copy=shared');
    });

    it('send a team copy to the lab running its environment', async () => {
      await harness.navigateByUrl('/environments/team');
      expect(url()).toBe('/labs/r2/details?copy=team');
    });

    it('send a copy\'s console to the console under its lab', async () => {
      await harness.navigateByUrl('/environments/team/console/101');
      expect(url()).toBe('/labs/r2/console/101?copy=team');
    });

    it('keep the standalone page for a copy no lab runs, or one that cannot be read', async () => {
      await harness.navigateByUrl('/environments/orphan');
      expect(url()).toBe('/environments/orphan');
      await harness.navigateByUrl('/environments/gone');
      expect(url()).toBe('/environments/gone');
    });
  });

  describe('a lab', () => {
    it('keeps its scoreboard at the old address', async () => {
      await setUp();
      await harness.navigateByUrl('/labs/r1/scoreboard');
      expect(deepest().routeConfig?.path).toBe('labs/:roomId/scoreboard');
    });

    it('opens its sub-pages inside the lab, which they read the lab id from', async () => {
      await setUp();
      await harness.navigateByUrl('/labs/r1/console/101?copy=t1');
      expect(deepest().routeConfig?.path).toBe('console/:vmid');
      expect(deepest().parent?.routeConfig?.path).toBe('labs/:labId');
      expect(deepest().params).toEqual({ labId: 'r1', vmid: '101' });
      expect(deepest().title).toBe('Console');
    });

    it('lets only authors into quick edit', async () => {
      await setUp('MEMBER');
      await harness.navigateByUrl('/labs/r1/edit');
      expect(url()).toBe('/home');
      TestBed.resetTestingModule();
      await setUp('CREATOR');
      await harness.navigateByUrl('/labs/r1/edit');
      expect(url()).toBe('/labs/r1/edit');
    });

    it('lets only admins and facilitators into Teams and Manage', async () => {
      await setUp('MEMBER');
      await harness.navigateByUrl('/labs/r1/teams');
      expect(url()).toBe('/home');
      TestBed.resetTestingModule();
      await setUp('FACILITATOR');
      await harness.navigateByUrl('/labs/r1/manage');
      expect(url()).toBe('/labs/r1/manage');
      await harness.navigateByUrl('/labs/r1/teams');
      expect(url()).toBe('/labs/r1/teams');
    });
  });
});
