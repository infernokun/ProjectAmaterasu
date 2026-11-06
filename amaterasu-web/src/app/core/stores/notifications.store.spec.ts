import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { Page } from '../../models/audit-event.model';
import { AppNotification } from '../../models/notification.model';
import { AuthService } from '../../services/auth.service';
import { WebsocketService } from '../../services/websocket.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { NotificationsService } from '../services/notifications.service';
import { NotificationsStore } from './notifications.store';

const note = (id: string, read = false): AppNotification => ({
  id, type: 'LAB_FAILED', title: `Note ${id}`, createdAt: '2026-11-06T10:00:00Z', readAt: read ? '2026-11-06T10:05:00Z' : null,
});

const page = (items: AppNotification[], last = true, number = 0): Page<AppNotification> => ({
  content: items, totalElements: items.length, totalPages: 1, number, size: 20, first: number === 0, last,
});

describe('NotificationsStore', () => {
  let auth: FakeAuthService;
  let socket: Subject<unknown>;
  let api: jasmine.SpyObj<NotificationsService>;

  beforeEach(() => {
    auth = new FakeAuthService();
    socket = new Subject();
    api = jasmine.createSpyObj<NotificationsService>('NotificationsService', ['list', 'unreadCount', 'markRead', 'markAllRead']);
    api.list.and.returnValue(of(page([note('a'), note('b', true)], false)));
    api.unreadCount.and.returnValue(of(1));
    api.markRead.and.callFake(id => of({ ...note(id), readAt: '2026-11-06T11:00:00Z' }));
    api.markAllRead.and.returnValue(of(1));
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: NotificationsService, useValue: api },
        { provide: WebsocketService, useValue: { getMessage: () => socket.asObservable() } },
      ],
    });
  });

  async function signedInStore() {
    auth.signIn({ id: 'u1', username: 'jmorales' }, 'MEMBER');
    const store = TestBed.inject(NotificationsStore);
    TestBed.tick();
    await new Promise(resolve => setTimeout(resolve));
    return store;
  }

  it('loads the first page and the unread count once signed in', async () => {
    const store = await signedInStore();
    expect(api.list).toHaveBeenCalledWith(0, 20);
    expect(store.items().map(item => item.id)).toEqual(['a', 'b']);
    expect(store.unread()).toBe(1);
    expect(store.hasUnread()).toBeTrue();
    expect(store.hasMore()).toBeTrue();
  });

  it('puts notifications from the socket on top, once', async () => {
    const store = await signedInStore();
    socket.next({ type: 'notification', notification: note('c') });
    socket.next({ type: 'notification', notification: note('c') });
    socket.next({ type: 'labTracker', labTracker: {} });
    expect(store.items().map(item => item.id)).toEqual(['c', 'a', 'b']);
    expect(store.unread()).toBe(2);
  });

  it('marks one read right away and keeps it read when the API agrees', async () => {
    const store = await signedInStore();
    await store.markRead('a');
    expect(api.markRead).toHaveBeenCalledWith('a');
    expect(store.items()[0].readAt).toBe('2026-11-06T11:00:00Z');
    expect(store.unread()).toBe(0);
    await store.markRead('b');
    expect(api.markRead).toHaveBeenCalledTimes(1);
  });

  it('puts the unread state back when marking fails', async () => {
    api.markRead.and.returnValue(throwError(() => new Error('down')));
    const store = await signedInStore();
    await store.markRead('a');
    expect(store.items()[0].readAt).toBeNull();
    expect(store.unread()).toBe(1);
  });

  it('marks everything read', async () => {
    const store = await signedInStore();
    await store.markAllRead();
    expect(api.markAllRead).toHaveBeenCalled();
    expect(store.unread()).toBe(0);
    expect(store.items().every(item => !!item.readAt)).toBeTrue();
  });

  it('loads older pages without duplicates', async () => {
    const store = await signedInStore();
    api.list.and.returnValue(of(page([note('b', true), note('d', true)], true, 1)));
    await store.loadMore();
    expect(api.list).toHaveBeenCalledWith(1, 20);
    expect(store.items().map(item => item.id)).toEqual(['a', 'b', 'd']);
    expect(store.hasMore()).toBeFalse();
  });

  it('empties on sign out', async () => {
    const store = await signedInStore();
    auth.signOutNow();
    TestBed.tick();
    expect(store.items()).toEqual([]);
    expect(store.unread()).toBe(0);
  });
});
