import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { SearchResults } from '../../models/search.model';
import { AuthService } from '../../services/auth.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { SearchService } from '../services/search.service';
import { SEARCH_DEBOUNCE_MS, SearchStore } from './search.store';

describe('SearchStore', () => {
  let auth: FakeAuthService;
  let api: jasmine.SpyObj<SearchService>;

  const results: SearchResults = {
    query: 'web',
    hits: [
      { type: 'CHALLENGE', id: 'c1', title: 'Web login', roomId: 'r1' },
      { type: 'TEAM_LAB', id: 't1', title: 'Web Exploitation 101', status: 'ACTIVE' },
      { type: 'LAB', id: 'l1', title: 'Web Exploitation 101' },
      { type: 'ROOM', id: 'r1', title: 'Web Range' },
    ],
  };

  beforeEach(() => {
    auth = new FakeAuthService();
    api = jasmine.createSpyObj<SearchService>('SearchService', ['search']);
    api.search.and.returnValue(of(results));
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: SearchService, useValue: api },
        { provide: SEARCH_DEBOUNCE_MS, useValue: 0 },
      ],
    });
  });

  async function settle(): Promise<void> {
    TestBed.tick();
    await new Promise(resolve => setTimeout(resolve));
    TestBed.tick();
  }

  function storeFor(roles: string) {
    auth.signIn({ id: 'u1', username: 'jmorales' }, roles);
    return TestBed.inject(SearchStore);
  }

  it('does not search until there are two characters', async () => {
    const store = storeFor('MEMBER');
    store.setQuery('w');
    await settle();
    expect(api.search).not.toHaveBeenCalled();
    expect(store.tooShort()).toBeTrue();
    expect(store.scope()).toBe('your team labs, the lab catalog, rooms and challenges');
  });

  it('groups results by type in a fixed order and numbers them for the keyboard', async () => {
    const store = storeFor('MEMBER');
    store.setQuery(' web ');
    await settle();
    expect(api.search).toHaveBeenCalledOnceWith('web');
    expect(store.groups().map(group => group.label)).toEqual(['Team labs', 'Lab catalog', 'Rooms', 'Challenges']);
    expect(store.flat().map(hit => hit.id)).toEqual(['t1', 'l1', 'r1', 'c1']);
    expect(store.groups()[3].items[0].index).toBe(3);
    expect(store.stale()).toBeFalse();
  });

  it('calls the catalog Labs for authors and widens the scope for admins', async () => {
    const store = storeFor('CREATOR');
    store.setQuery('web');
    await settle();
    expect(store.groups()[1].label).toBe('Labs');
    expect(store.scope()).toBe('every lab, room and challenge');
  });

  it('keeps only the answer to the latest query', async () => {
    const slow = new Subject<SearchResults>();
    api.search.and.returnValues(slow, of({ query: 'room', hits: [{ type: 'ROOM', id: 'r2', title: 'Room two' }] }));
    const store = storeFor('MEMBER');
    store.setQuery('web');
    await settle();
    expect(store.loading()).toBeTrue();
    store.setQuery('room');
    await settle();
    slow.next(results);
    expect(store.flat().map(hit => hit.id)).toEqual(['r2']);
  });

  it('reports an error and clears old results', async () => {
    api.search.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Database down' } })));
    const store = storeFor('MEMBER');
    store.setQuery('web');
    await settle();
    expect(store.error()).toBe('Database down');
    expect(store.flat()).toEqual([]);
  });

  it('clears the query', async () => {
    const store = storeFor('MEMBER');
    store.setQuery('web');
    await settle();
    store.clear();
    await settle();
    expect(store.query()).toBe('');
    expect(store.flat()).toEqual([]);
  });
});
