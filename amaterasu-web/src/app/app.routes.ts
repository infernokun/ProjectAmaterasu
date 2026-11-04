import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { AppInitGuard } from './guards/app-init.guard';
import { authGuard, devToolsGuard, roleGuard, signedOutGuard } from './guards/auth.guard';
import { AREA_ROLES } from './core/auth/roles';
import { ShellComponent } from './shell/shell.component';

/** Old addresses, kept working for bookmarks and links in the wild. */
const LEGACY_REDIRECTS: Routes = [
  { path: 'challenges', redirectTo: '/rooms' },
  { path: 'room/:room', redirectTo: ({ params }) => inject(Router).createUrlTree(['/rooms', params['room']]) },
  { path: 'room/:room/scoreboard', redirectTo: ({ params }) => inject(Router).createUrlTree(['/rooms', params['room'], 'scoreboard']) },
  {
    path: 'room/:room/scoreboard/:username',
    redirectTo: ({ params }) => inject(Router).createUrlTree(['/rooms', params['room'], 'scoreboard', params['username']]),
  },
  {
    path: 'lab/settings/:name',
    redirectTo: ({ queryParams }) => inject(Router).createUrlTree(queryParams['id'] ? ['/labs', queryParams['id']] : ['/labs']),
  },
  { path: 'users', redirectTo: '/admin/users' },
  { path: 'teams', redirectTo: '/admin/teams' },
  { path: 'remote-server', redirectTo: '/admin/servers' },
  { path: 'log', redirectTo: '/admin/audit' },
  { path: 'team-log', redirectTo: '/admin/audit' },
  { path: 'vm-lab-builder', redirectTo: '/build/labs' },
  { path: 'code', redirectTo: '/dev/code' },
  { path: 'login', redirectTo: '/welcome' },
];

export const routes: Routes = [
  {
    path: 'init',
    title: 'Set up',
    loadComponent: () => import('./components/initialization/initialization.component').then(m => m.InitializationComponent),
  },
  {
    path: 'welcome',
    title: 'Sign in',
    canActivate: [AppInitGuard, signedOutGuard],
    loadComponent: () => import('./shell/landing.component').then(m => m.LandingComponent),
  },
  ...LEGACY_REDIRECTS,
  {
    path: '',
    component: ShellComponent,
    canActivate: [AppInitGuard, authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      { path: '', loadChildren: () => import('./features/play/play.routes').then(m => m.PLAY_ROUTES) },
      {
        path: 'run',
        canActivate: [roleGuard(...AREA_ROLES.run)],
        loadChildren: () => import('./features/run/run.routes').then(m => m.RUN_ROUTES),
      },
      {
        path: 'build',
        canActivate: [roleGuard(...AREA_ROLES.build)],
        loadChildren: () => import('./features/build/build.routes').then(m => m.BUILD_ROUTES),
      },
      {
        path: 'admin',
        canActivate: [roleGuard(...AREA_ROLES.admin)],
        loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES),
      },
      { path: 'account', loadChildren: () => import('./features/account/account.routes').then(m => m.ACCOUNT_ROUTES) },
      {
        path: 'dev',
        canActivate: [devToolsGuard],
        loadChildren: () => import('./features/dev/dev.routes').then(m => m.DEV_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
