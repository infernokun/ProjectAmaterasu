import { Routes } from '@angular/router';
import { page } from '../../core/page';

const builder = () => import('./labs/lab-builder.page').then(m => m.LabBuilderPage);

export const BUILD_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'labs' },
  page({
    path: 'labs',
    loadComponent: () => import('./rooms/room-list.page').then(m => m.RoomListPage),
    data: { section: 'Build', title: 'Labs' },
  }),
  page({
    path: 'labs/:roomId',
    loadComponent: () => import('./rooms/room-editor.page').then(m => m.RoomEditorPage),
    data: { section: 'Build', title: 'Lab' },
  }),
  page({
    path: 'environments',
    loadComponent: () => import('./labs/lab-list.page').then(m => m.LabListPage),
    data: { section: 'Build', title: 'Environments' },
  }),
  page({ path: 'environments/new', loadComponent: builder, data: { section: 'Build', title: 'New environment' } }),
  page({ path: 'environments/:labId', loadComponent: builder, data: { section: 'Build', title: 'Environment' } }),
];
