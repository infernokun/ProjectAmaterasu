import { Routes } from '@angular/router';
import { page } from '../../core/page';

const placeholder = () => import('../../shared/ui/placeholder-page.component').then(m => m.PlaceholderPageComponent);

// Screens marked legacy still use the previous components until they are rebuilt.
export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  page({
    path: 'users',
    loadComponent: () => import('../../components/users/users.component').then(m => m.UsersComponent),
    data: { section: 'Admin', title: 'Users and teams', legacy: true },
  }),
  page({
    path: 'teams',
    loadComponent: () => import('../../components/teams/teams.component').then(m => m.TeamsComponent),
    data: { section: 'Admin', title: 'Users and teams', legacy: true },
  }),
  page({
    path: 'servers',
    loadComponent: () => import('../../components/app-lab/remote-server/remote-server.component').then(m => m.RemoteServerComponent),
    data: { section: 'Admin', title: 'Servers', legacy: true },
  }),
  page({
    path: 'audit',
    loadComponent: placeholder,
    data: { section: 'Admin', title: 'Audit log', description: 'Lab, user and score changes, newest first.' },
  }),
];
