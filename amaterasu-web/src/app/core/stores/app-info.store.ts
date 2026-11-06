import { computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { signalStore, withComputed, withProps } from '@ngrx/signals';
import { ApplicationInfo } from '../../models/application-info.model';
import { AppInitService } from '../../services/app-init.service';
import { EnvironmentService } from '../../services/environment.service';

/** Application name, description and setup state from /application-info. */
export const AppInfoStore = signalStore(
  { providedIn: 'root' },
  withProps(() => {
    const init = inject(AppInitService);
    return {
      _info: toSignal(init.getApplicationInfo(), { initialValue: new ApplicationInfo({}) }),
      initialized: toSignal(init.isInitialized(), { initialValue: false }),
      _environment: inject(EnvironmentService),
    };
  }),
  withComputed(({ _info, _environment }) => ({
    name: computed(() => {
      const name = _info().name;
      return name && name !== 'unknown' ? name : 'Amaterasu';
    }),
    description: computed(() => {
      const description = _info().description;
      return description && description !== 'unknown' ? description : 'Labs and CTF';
    }),
    /** Parsed settings JSON; empty when missing or invalid. */
    settings: computed<Record<string, unknown>>(() => {
      try {
        const parsed = JSON.parse(_info().settings || '{}');
        return parsed && typeof parsed === 'object' ? parsed : {};
      } catch {
        return {};
      }
    }),
    /** Developer pages such as /dev/code are only routed when the environment turns them on. */
    devTools: computed(() => _environment.settings?.devTools === true),
  })),
);
