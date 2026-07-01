import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

import { routes } from './app.routes';
import { EnvironmentService } from './services/environment.service';
import { AppInitService } from './services/app-init.service';
import { AuthInterceptor } from './services/auth/auth-interceptor.service';

// Loads runtime environment settings before the app renders, then runs
// app-level initialization. Runs in an injection context, so `inject()` works.
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
    EnvironmentService,
    AppInitService,
    provideAppInitializer(initializeApp),
  ],
};
