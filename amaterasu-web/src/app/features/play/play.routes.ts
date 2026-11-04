import { Routes } from '@angular/router';
import { page } from '../../core/page';

const placeholder = () => import('../../shared/ui/placeholder-page.component').then(m => m.PlaceholderPageComponent);

// Screens marked legacy still use the previous components until they are rebuilt.
export const PLAY_ROUTES: Routes = [
  page({
    path: 'home',
    loadComponent: () => import('../../components/home/home.component').then(m => m.HomeComponent),
    data: { title: 'Home', legacy: true },
  }),
  page({
    path: 'labs',
    loadComponent: placeholder,
    data: { title: 'Labs', description: "Your team's labs, each with a console for services, logs, files and activity." },
  }),
  page({
    path: 'labs/:trackerId',
    loadComponent: () => import('../../components/app-lab/lab-settings/lab-settings.component').then(m => m.LabSettingsComponent),
    data: { title: 'Lab console', legacy: true },
  }),
  page({
    path: 'rooms',
    loadComponent: () => import('../../components/app-ctf/room/room.component').then(m => m.RoomComponent),
    data: { title: 'Rooms', legacy: true },
  }),
  page({
    path: 'rooms/:roomId',
    loadComponent: () => import('../../components/app-ctf/ctf/ctf-main/ctf-main.component').then(m => m.CTFMainComponent),
    data: { title: 'Room', legacy: true },
  }),
  page({
    path: 'rooms/:roomId/scoreboard',
    loadComponent: () => import('../../components/app-ctf/scoreboard/scoreboard.component').then(m => m.ScoreboardComponent),
    data: { title: 'Scoreboard', legacy: true },
  }),
  page({
    path: 'rooms/:roomId/scoreboard/:username',
    loadComponent: () => import('../../components/app-ctf/player-scoreboard/player-scoreboard.component').then(m => m.PlayerScoreboardComponent),
    data: { title: 'Player', legacy: true },
  }),
  page({
    path: 'scoreboard',
    loadComponent: placeholder,
    data: { title: 'Scoreboard', description: 'Players and teams in your current room, with points over time.' },
  }),
];
