import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MaterialModule } from './material.module';
import { HomeComponent } from './components/home/home.component';
import { EnvironmentService } from './services/environment/environment.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { LabComponent } from './components/lab/lab.component';
import { CommonModule } from '@angular/common';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { DialogComponent } from './components/common/dialog/dialog.component';
import { BashColoringPipe } from './pipes/bash-coloring.pipe';

export function init_app(environmentService: EnvironmentService) {
  return () => environmentService.load().then(() => {
    console.log('App initialized');
  });
}

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    LabComponent,
    AuditLogComponent,
    DialogComponent,
    BashColoringPipe
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    ReactiveFormsModule,
    FormsModule,
    MaterialModule,
    CommonModule
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: init_app,
      deps: [EnvironmentService],
      multi: true,
    },
    provideHttpClient(withInterceptorsFromDi())
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
