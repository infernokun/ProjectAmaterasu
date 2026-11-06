import { DOCUMENT, effect, inject, untracked } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { readStorage, writeStorage } from '../storage';
import { SessionStore } from '../stores/session.store';

export type Density = 'comfortable' | 'compact';

/** The last choice on this device; read by src/theme-init.js before the app starts. */
export const DEVICE_DENSITY_KEY = 'amaterasu.density';
export const userDensityKey = (userId: string) => `amaterasu.density.${userId}`;

function parse(value: string | null): Density | null {
  return value === 'comfortable' || value === 'compact' ? value : null;
}

/**
 * How tightly pages are laid out. Saved per user in this browser like the theme, and applied as
 * data-density on <html>, where the design tokens pick smaller spacing for compact.
 */
export const DensityStore = signalStore(
  { providedIn: 'root' },
  withState(() => ({ density: parse(readStorage(DEVICE_DENSITY_KEY)) ?? ('comfortable' as Density) })),
  withMethods((store, session = inject(SessionStore)) => ({
    setDensity(density: Density): void {
      patchState(store, { density });
      writeStorage(DEVICE_DENSITY_KEY, density);
      const userId = session.user()?.id;
      if (userId) {
        writeStorage(userDensityKey(userId), density);
      }
    },
  })),
  withHooks({
    onInit(store, document = inject(DOCUMENT), session = inject(SessionStore)) {
      // A signed-in user's own choice wins over whatever this device used last
      effect(() => {
        const userId = session.user()?.id;
        const saved = userId ? parse(readStorage(userDensityKey(userId))) : null;
        if (saved && saved !== untracked(store.density)) {
          patchState(store, { density: saved });
          writeStorage(DEVICE_DENSITY_KEY, saved);
        }
      });

      effect(() => {
        const root = document.documentElement;
        if (store.density() === 'compact') {
          root.setAttribute('data-density', 'compact');
        } else {
          root.removeAttribute('data-density');
        }
      });
    },
  }),
);
