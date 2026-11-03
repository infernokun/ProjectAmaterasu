import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

import { routes } from './app.routes';
import { EnvironmentService } from './services/environment.service';
import { AppInitService } from './services/app-init.service';
import { AuthInterceptor } from './services/auth/auth-interceptor.service';

function initializeApp(): Promise<void> {
  const environmentService = inject(EnvironmentService);
  const appInitService = inject(AppInitService);

  return environmentService.load().then(() => {
    console.log('🔧 Environment loaded successfully');

    if (!environmentService.settings?.restUrl) {
      console.error('🔧 Environment loaded but REST URL is still undefined!');
      throw new Error('Failed to load environment settings');
    }

    return appInitService.load(environmentService);
  }).then(() => {
    console.log('🔧 App initialization completed successfully');
  }).catch((error) => {
    console.error('🔧 App initialization failed:', error);
    throw error;
  });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    provideCharts(withDefaultRegisterables()),
    provideNativeDateAdapter(),
    EnvironmentService,
    AppInitService,
    provideAppInitializer(initializeApp),
  ],
};
