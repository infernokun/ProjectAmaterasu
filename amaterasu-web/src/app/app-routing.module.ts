import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { TeamAuditLogComponent } from './components/team-audit-log/team-audit-log.component';
import { CodeBlockComponent } from './components/common/code-block/code-block.component';
import { RemoteServerComponent } from './components/remote-server/remote-server.component';
import { LabSettingsComponent } from './components/lab-settings/lab-settings.component';
import { AppInitComponent } from './components/app-init/app-init.component';
import { UsersComponent } from './components/users/users.component';
import { AppInitGuard } from './guards/app-init.guard';
import { TeamsComponent } from './components/teams/teams.component';
import { VMLabBuilderComponent } from './components/vm-lab-builder/vm-lab-builder.component';

const routes: Routes = [
  { path: 'vm-lab-builder', component: VMLabBuilderComponent, canActivate: [AppInitGuard] },
  { path: 'teams', component: TeamsComponent, canActivate: [AppInitGuard] },
  { path: 'users', component: UsersComponent, canActivate: [AppInitGuard] },
  { path: 'lab/settings/:name', component: LabSettingsComponent, canActivate: [AppInitGuard] },
  { path: 'remote-server', component: RemoteServerComponent, canActivate: [AppInitGuard] },
  { path: 'code', component: CodeBlockComponent, canActivate: [AppInitGuard] },
  { path: 'team-log', component: TeamAuditLogComponent, canActivate: [AppInitGuard] },
  { path: 'log', component: AuditLogComponent, canActivate: [AppInitGuard] },
  { path: 'home', component: HomeComponent, canActivate: [AppInitGuard] },
  { path: 'init', component: AppInitComponent },
  {
    path: '',
    redirectTo: '/home', // Will be redirected to home if initialized
    pathMatch: 'full',
  },
  { path: '**', redirectTo: '' } // Wildcard route for a 404 page, redirect to root
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
