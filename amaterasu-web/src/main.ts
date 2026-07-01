import { bootstrapApplication } from '@angular/platform-browser';

import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));

(window as any).MonacoEnvironment = {
  getWorkerUrl: function (_moduleId: any, label: any) {
    return '/assets/monaco-editor/min/vs/base/worker/workerMain.js';
  },
};

const originalConsoleError = console.error;
console.error = function (...args: any[]) {
  if (args[1] && args[1].toString().includes("L is null")) {
    return; // Suppress the Monaco bug message
  }
  originalConsoleError.apply(console, args);
};
