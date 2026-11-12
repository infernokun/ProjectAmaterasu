import { ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { TitleStrategy, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideNativeDateAdapter } from '@angular/material/core';

import { routes } from './app.routes';
import { EnvironmentService } from './services/environment.service';
import { AppInitService } from './services/app-init.service';
import { AuthInterceptor } from './services/auth/auth-interceptor.service';
import { AmaterasuTitleStrategy } from './core/title.strategy';

async function initializeApp(): Promise<void> {
  const environmentService = inject(EnvironmentService);
  const appInitService = inject(AppInitService);

  await environmentService.load();
  if (!environmentService.settings?.restUrl) {
    throw new Error('Failed to load environment settings');
  }
  appInitService.load(environmentService);
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    // A lab's sub-pages read the lab's :labId
    provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
    { provide: TitleStrategy, useClass: AmaterasuTitleStrategy },
    provideAnimations(),
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    provideNativeDateAdapter(),
    EnvironmentService,
    AppInitService,
    provideAppInitializer(initializeApp),
  ],
};
