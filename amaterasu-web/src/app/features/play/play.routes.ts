import { Routes } from '@angular/router';
import { page } from '../../core/page';

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
    path: 'labs/:labId',
    loadComponent: () => import('./labs/lab.page').then(m => m.LabPage),
    data: { title: 'Lab' },
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
  page({
    path: 'environments/:trackerId',
    loadComponent: () => import('./labs/lab-console.page').then(m => m.LabConsolePage),
    data: { title: 'Environment' },
  }),
  page({
    path: 'environments/:trackerId/console/:vmid',
    loadComponent: () => import('./labs/vm-console.page').then(m => m.VmConsolePage),
    data: { title: 'VM console' },
  }),
  page({
    path: 'scoreboard',
    loadComponent: () => import('./scoreboard/scoreboard.page').then(m => m.ScoreboardPage),
    data: { title: 'Scoreboard' },
  }),
];
