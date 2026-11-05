import { Routes } from '@angular/router';
import { page } from '../../core/page';

const builder = () => import('./labs/lab-builder.page').then(m => m.LabBuilderPage);

export const BUILD_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'labs' },
  page({
    path: 'labs',
    loadComponent: () => import('./labs/lab-list.page').then(m => m.LabListPage),
    data: { section: 'Build', title: 'Labs' },
  }),
  page({ path: 'labs/new', loadComponent: builder, data: { section: 'Build', title: 'New lab' } }),
  page({ path: 'labs/:labId', loadComponent: builder, data: { section: 'Build', title: 'Lab' } }),
  page({
    path: 'rooms',
    loadComponent: () => import('./rooms/room-list.page').then(m => m.RoomListPage),
    data: { section: 'Build', title: 'Rooms and challenges' },
  }),
  page({
    path: 'rooms/:roomId',
    loadComponent: () => import('./rooms/room-editor.page').then(m => m.RoomEditorPage),
    data: { section: 'Build', title: 'Room' },
  }),
];
