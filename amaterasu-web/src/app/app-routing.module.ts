import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { TeamAuditLogComponent } from './components/team-audit-log/team-audit-log.component';

const routes: Routes = [
  { path: 'team-log', component: TeamAuditLogComponent },
  { path: 'log', component: AuditLogComponent },
  { path: 'home', component: HomeComponent },
  { path: '', component: HomeComponent }, // Default route
  { path: '**', redirectTo: '' } // Wildcard route for a 404 page, redirect to home
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
