import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MaterialModule } from './material.module';
import { HomeComponent } from './components/home/home.component';
import { EnvironmentService } from './services/environment.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { LabComponent } from './components/lab/lab.component';
import { CommonModule } from '@angular/common';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { DialogComponent } from './components/common/dialog/dialog.component';
import { BashColoringPipe } from './pipes/bash-coloring.pipe';
import { CodeBlockComponent } from './components/common/code-block/code-block.component';

import { CodeEditorModule, provideCodeEditor } from '@ngstack/code-editor';
import { RemoteServerComponent } from './components/remote-server/remote-server.component';
import { DurationPipe } from './pipes/duration.pipe';
import { DialogQuestionComponent } from './components/common/dialog-question/dialog-question.component';
import { AddDialogFormComponent } from './components/common/add-dialog-form/add-dialog-form.component';
import { SkeletonRectComponent } from './components/common/skeleton-rect/skeleton-rect.component';
import { SkeletonDirective } from './directives/skeleton.directive';
import { LabSettingsComponent } from './components/lab-settings/lab-settings.component';
import { DragnDropDirective } from './directives/dragndrop.directive';
import { AppInitComponent } from './components/app-init/app-init.component';
import { LoginComponent } from './components/common/login/login.component';
import { RegisterComponent } from './components/common/register/register.component';

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
    CodeBlockComponent,
    BashColoringPipe,
    RemoteServerComponent,
    DurationPipe,
    DialogQuestionComponent,
    AddDialogFormComponent,
    SkeletonRectComponent,
    SkeletonDirective,
    LabSettingsComponent,
    DragnDropDirective,
    AppInitComponent,
    LoginComponent,
    RegisterComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    ReactiveFormsModule,
    FormsModule,
    MaterialModule,
    CommonModule,
    CodeEditorModule
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: init_app,
      deps: [EnvironmentService],
      multi: true,
    },
    provideHttpClient(withInterceptorsFromDi()),
    provideCodeEditor({
      editorVersion: '0.44.0'
    })
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
