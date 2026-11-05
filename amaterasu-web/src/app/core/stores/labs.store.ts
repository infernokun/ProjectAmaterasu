import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, tap } from 'rxjs';
import { Lab } from '../../models/lab/lab.model';
import { LabService } from '../../services/lab/lab.service';
import { errorMessage } from '../http-error';
import { RequestState, initialRequestState } from './request-status';

interface LabsState extends RequestState {
  labs: Lab[];
}

/** The lab catalog: every lab a team can start. */
export const LabsStore = signalStore(
  { providedIn: 'root' },
  withState<LabsState>({ labs: [], ...initialRequestState }),
  withComputed(({ labs, status }) => ({
    loading: computed(() => status() === 'loading'),
    byId: computed(() => new Map(labs().map(lab => [lab.id!, lab]))),
    readyLabs: computed(() => labs().filter(lab => lab.ready)),
  })),
  withMethods((store, labService = inject(LabService)) => {
    const fetch = rxMethod<void>(pipe(
      tap(() => patchState(store, { status: 'loading', error: null })),
      exhaustMap(() => labService.getLabs().pipe(
        tap(labs => patchState(store, { labs, status: 'loaded' })),
        catchError(error => {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Labs could not be loaded.' });
          return EMPTY;
        }),
      )),
    ));
    return {
      /** Loads once; pass true to reload. */
      load(force = false): void {
        if (force || store.status() === 'idle' || store.status() === 'error') {
          fetch();
        }
      },
      upsert(lab: Lab): void {
        const others = store.labs().filter(existing => existing.id !== lab.id);
        patchState(store, { labs: [...others, lab].sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '')) });
      },
      remove(labId: string): void {
        patchState(store, { labs: store.labs().filter(lab => lab.id !== labId) });
      },
      reset(): void {
        patchState(store, { labs: [], ...initialRequestState });
      },
    };
  }),
);
