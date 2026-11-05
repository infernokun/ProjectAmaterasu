import { DOCUMENT, computed, effect, inject, untracked } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { readStorage, writeStorage } from '../storage';
import { SessionStore } from '../stores/session.store';

export type ThemePreference = 'system' | 'light' | 'dark';
export type Theme = 'light' | 'dark';

/** The last choice on this device; read by src/theme-init.js before the app starts. */
export const DEVICE_THEME_KEY = 'amaterasu.theme';
export const userThemeKey = (userId: string) => `amaterasu.theme.${userId}`;

function parse(value: string | null): ThemePreference | null {
  return value === 'system' || value === 'light' || value === 'dark' ? value : null;
}

/**
 * Theme setting: match the system, or always light or dark. Saved per user in this browser and
 * applied as data-theme on <html>, which the design tokens key off.
 */
export const ThemeStore = signalStore(
  { providedIn: 'root' },
  withState(() => ({
    preference: parse(readStorage(DEVICE_THEME_KEY)) ?? ('system' as ThemePreference),
    systemDark: true,
  })),
  withComputed(({ preference, systemDark }) => ({
    theme: computed<Theme>(() => {
      const pref = preference();
      return pref === 'system' ? (systemDark() ? 'dark' : 'light') : pref;
    }),
  })),
  withMethods((store, session = inject(SessionStore)) => ({
    setPreference(preference: ThemePreference): void {
      patchState(store, { preference });
      writeStorage(DEVICE_THEME_KEY, preference);
      const userId = session.user()?.id;
      if (userId) {
        writeStorage(userThemeKey(userId), preference);
      }
    },
  })),
  withHooks({
    onInit(store, document = inject(DOCUMENT), session = inject(SessionStore)) {
      const media = document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
      if (media) {
        patchState(store, { systemDark: media.matches });
        media.addEventListener('change', event => patchState(store, { systemDark: event.matches }));
      }

      // A signed-in user's own choice wins over whatever this device used last
      effect(() => {
        const userId = session.user()?.id;
        const saved = userId ? parse(readStorage(userThemeKey(userId))) : null;
        if (saved && saved !== untracked(store.preference)) {
          patchState(store, { preference: saved });
          writeStorage(DEVICE_THEME_KEY, saved);
        }
      });

      effect(() => {
        const preference = store.preference();
        const root = document.documentElement;
        if (preference === 'system') {
          root.removeAttribute('data-theme');
        } else {
          root.setAttribute('data-theme', preference);
        }
      });
    },
  }),
);
