import { InjectionToken, computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, debounceTime, distinctUntilChanged, map, of, pipe, switchMap, tap } from 'rxjs';
import { SearchHit, SearchHitType } from '../../models/search.model';
import { errorMessage } from '../http-error';
import { SearchService } from '../services/search.service';
import { RequestState, initialRequestState } from './request-status';
import { SessionStore } from './session.store';

/** How long typing pauses before a search goes out. */
export const SEARCH_DEBOUNCE_MS = new InjectionToken<number>('SEARCH_DEBOUNCE_MS', { factory: () => 200 });

export const MIN_SEARCH = 2;

/** Result groups in the order the popover shows them. */
const GROUPS: readonly SearchHitType[] = ['TEAM_LAB', 'LAB', 'ROOM', 'CHALLENGE', 'USER', 'TEAM', 'SERVER'];

export interface SearchGroup {
  type: SearchHitType;
  label: string;
  /** Each hit with its position in the whole list, for keyboard movement. */
  items: { hit: SearchHit; index: number }[];
}

interface SearchState extends RequestState {
  query: string;
  hits: SearchHit[];
  /** The query the hits answer. */
  answered: string | null;
}

/** The top bar search: the query, debounced requests and results grouped by type. */
export const SearchStore = signalStore(
  { providedIn: 'root' },
  withState<SearchState>({ query: '', hits: [], answered: null, ...initialRequestState }),
  withComputed(({ query, hits, answered, status }, session = inject(SessionStore)) => {
    const groups = computed<SearchGroup[]>(() => {
      let index = 0;
      return GROUPS.map(type => ({
        type,
        label: groupLabel(type),
        items: hits().filter(hit => hit.type === type).map(hit => ({ hit, index: index++ })),
      })).filter(group => group.items.length > 0);
    });
    return {
      trimmed: computed(() => query().trim()),
      tooShort: computed(() => query().trim().length < MIN_SEARCH),
      groups,
      flat: computed(() => groups().flatMap(group => group.items.map(item => item.hit))),
      loading: computed(() => status() === 'loading'),
      /** The hits are for an older query while a newer one is on its way. */
      stale: computed(() => answered() !== query().trim()),
      /** What the search covers, for the hint under an empty box. */
      scope: computed(() => session.isAdmin()
        ? 'labs, environments, questions, users, teams and servers'
        : session.canBuild() ? 'every lab, environment and question' : 'your team environments, labs and questions'),
    };
  }),
  withMethods((store, api = inject(SearchService), wait = inject(SEARCH_DEBOUNCE_MS)) => {
    const run = rxMethod<string>(pipe(
      map(query => query.trim()),
      debounceTime(wait),
      distinctUntilChanged(),
      switchMap(query => {
        if (query.length < MIN_SEARCH) {
          patchState(store, { hits: [], answered: query, status: 'idle', error: null });
          return of(null);
        }
        patchState(store, { status: 'loading', error: null });
        return api.search(query).pipe(
          tap(results => patchState(store, { hits: results.hits ?? [], answered: query, status: 'loaded' })),
          catchError(error => {
            patchState(store, { hits: [], answered: query, status: 'error', error: errorMessage(error) ?? 'Search did not answer.' });
            return EMPTY;
          }),
        );
      }),
    ));
    run(store.query);
    return {
      setQuery(query: string): void {
        patchState(store, { query });
      },
      clear(): void {
        patchState(store, { query: '' });
      },
    };
  }),
);

export function groupLabel(type: SearchHitType): string {
  switch (type) {
    case 'TEAM_LAB': return 'Team environments';
    case 'LAB': return 'Environments';
    case 'ROOM': return 'Labs';
    case 'CHALLENGE': return 'Questions';
    case 'USER': return 'Users';
    case 'TEAM': return 'Teams';
    case 'SERVER': return 'Servers';
  }
}
