import { Routes } from '@angular/router';
import { page } from '../../core/page';

export const ACCOUNT_ROUTES: Routes = [
  page({
    path: '',
    loadComponent: () => import('./account.page').then(m => m.AccountPage),
    data: { title: 'Account', description: 'Profile, password, sessions and appearance.' },
  }),
];
