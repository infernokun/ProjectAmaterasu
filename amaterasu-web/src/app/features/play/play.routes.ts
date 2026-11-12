import { Routes } from '@angular/router';
import { AREA_ROLES } from '../../core/auth/roles';
import { page } from '../../core/page';
import { roleGuard } from '../../guards/auth.guard';
import { copyAddressGuard } from './labs/lab-address';
import { leaveEditGuard } from './labs/edit/leave-edit.guard';

const details = () => import('./labs/lab-console.page').then(m => m.LabConsolePage);
const vmConsole = () => import('./labs/vm-console.page').then(m => m.VmConsolePage);

export const PLAY_ROUTES: Routes = [
  page({
    path: 'home',
    loadComponent: () => import('./home/home.page').then(m => m.HomePage),
    data: { title: 'Home' },
  }),
  page({
    path: 'labs',
    loadComponent: () => import('./labs/labs.page').then(m => m.LabsPage),
    data: { title: 'Labs' },
  }),
  page({
    path: 'labs/:roomId/scoreboard',
    loadComponent: () => import('./scoreboard/scoreboard.page').then(m => m.ScoreboardPage),
    data: { title: 'Scoreboard' },
  }),
  page({
    path: 'labs/:roomId/scoreboard/:username',
    loadComponent: () => import('./scoreboard/player.page').then(m => m.PlayerPage),
    data: { title: 'Player' },
  }),
  // One lab: the questions on the left; on wide screens these open beside them, otherwise as a page
  {
    ...page({
      path: 'labs/:labId',
      loadComponent: () => import('./labs/lab.page').then(m => m.LabPage),
      data: { title: 'Lab' },
    }),
    children: [
      page({ path: 'details', loadComponent: details, data: { title: 'Details' } }),
      page({ path: 'console/:vmid', loadComponent: vmConsole, data: { title: 'Console' } }),
      page({
        path: 'edit',
        canActivate: [roleGuard(...AREA_ROLES.build)],
        canDeactivate: [leaveEditGuard],
        loadComponent: () => import('./labs/edit/lab-quick-edit.page').then(m => m.LabQuickEditPage),
        data: { title: 'Edit' },
      }),
      page({
        path: 'teams',
        canActivate: [roleGuard(...AREA_ROLES.run)],
        loadComponent: () => import('./labs/teams/lab-teams.page').then(m => m.LabTeamsPage),
        data: { title: 'Teams' },
      }),
      page({
        path: 'manage',
        canActivate: [roleGuard(...AREA_ROLES.run)],
        loadComponent: () => import('./labs/manage/lab-manage.page').then(m => m.LabManagePage),
        data: { title: 'Manage' },
      }),
    ],
  },
  // Old addresses of a copy; they move under the copy's lab, and a copy no lab runs keeps this page
  page({ path: 'environments/:trackerId', canActivate: [copyAddressGuard], loadComponent: details, data: { title: 'Environment' } }),
  page({
    path: 'environments/:trackerId/console/:vmid',
    canActivate: [copyAddressGuard],
    loadComponent: vmConsole,
    data: { title: 'VM console' },
  }),
  page({
    path: 'scoreboard',
    loadComponent: () => import('./scoreboard/scoreboard.page').then(m => m.ScoreboardPage),
    data: { title: 'Scoreboard' },
  }),
];
