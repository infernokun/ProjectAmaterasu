import { Routes } from '@angular/router';
import { page } from '../../core/page';

export const DEV_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'code' },
  page({
    path: 'code',
    loadComponent: () => import('../../components/common/code-block/code-block.component').then(m => m.CodeBlockComponent),
    data: { section: 'Dev', title: 'Code', legacy: true },
  }),
];
