import { Routes } from '@angular/router';
import { page } from '../../core/page';

export const RUN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'live' },
  page({
    path: 'live',
    loadComponent: () => import('./live/live.page').then(m => m.LivePage),
    data: { section: 'Run', title: 'Live event' },
  }),
];
