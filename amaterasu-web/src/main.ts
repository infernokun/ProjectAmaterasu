import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';

platformBrowserDynamic()
  .bootstrapModule(AppModule, {
    ngZoneEventCoalescing: true,
  })
  .catch((err) => console.error(err));

(window as any).MonacoEnvironment = {
  getWorkerUrl: function (_moduleId: any, label: any) {
    return '/assets/monaco-editor/min/vs/base/worker/workerMain.js';
  },
};
