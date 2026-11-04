import { Routes } from '@angular/router';
import { page } from '../../core/page';

const placeholder = () => import('../../shared/ui/placeholder-page.component').then(m => m.PlaceholderPageComponent);

export const RUN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'live' },
  page({
    path: 'live',
    loadComponent: placeholder,
    data: { section: 'Run', title: 'Live event', description: 'Lab failures, wrong flag spikes and offline servers as they happen, plus awarding points.' },
  }),
];
