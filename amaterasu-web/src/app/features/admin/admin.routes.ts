import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { page } from '../../core/page';

export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  page({
    path: 'users',
    loadComponent: () => import('./users/admin-users.page').then(m => m.AdminUsersPage),
    data: { section: 'Admin', title: 'Users and teams' },
  }),
  { path: 'teams', redirectTo: () => inject(Router).createUrlTree(['/admin/users'], { queryParams: { tab: 'teams' } }) },
  page({
    path: 'servers',
    loadComponent: () => import('./servers/admin-servers.page').then(m => m.AdminServersPage),
    data: { section: 'Admin', title: 'Servers' },
  }),
  page({
    path: 'audit',
    loadComponent: () => import('./audit/audit.page').then(m => m.AuditPage),
    data: { section: 'Admin', title: 'Audit log' },
  }),
];
