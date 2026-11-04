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
    path: 'labs/:trackerId',
    loadComponent: () => import('./labs/lab-console.page').then(m => m.LabConsolePage),
    data: { title: 'Lab console' },
  }),
  page({
    path: 'rooms',
    loadComponent: () => import('./rooms/rooms.page').then(m => m.RoomsPage),
    data: { title: 'Rooms' },
  }),
  page({
    path: 'rooms/:roomId',
    loadComponent: () => import('./rooms/room.page').then(m => m.RoomPage),
    data: { title: 'Room' },
  }),
  page({
    path: 'rooms/:roomId/scoreboard',
    loadComponent: () => import('./scoreboard/scoreboard.page').then(m => m.ScoreboardPage),
    data: { title: 'Scoreboard' },
  }),
  page({
    path: 'rooms/:roomId/scoreboard/:username',
    loadComponent: () => import('./scoreboard/player.page').then(m => m.PlayerPage),
    data: { title: 'Player' },
  }),
  page({
    path: 'scoreboard',
    loadComponent: () => import('./scoreboard/scoreboard.page').then(m => m.ScoreboardPage),
    data: { title: 'Scoreboard' },
  }),
];
