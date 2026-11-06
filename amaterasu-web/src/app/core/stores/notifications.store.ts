import { computed, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { filter, firstValueFrom, forkJoin } from 'rxjs';
import { AppNotification } from '../../models/notification.model';
import { WebsocketService } from '../../services/websocket.service';
import { errorMessage } from '../http-error';
import { NotificationsService } from '../services/notifications.service';
import { RequestState, initialRequestState } from './request-status';
import { SessionStore } from './session.store';

export const NOTIFICATION_PAGE = 20;

/** What the API pushes over the socket when a notification is created for this user. */
export interface NotificationSocketMessage {
  type: 'notification';
  notification: AppNotification;
}

export function isNotificationMessage(message: unknown): message is NotificationSocketMessage {
  return !!message && typeof message === 'object' && (message as { type?: unknown }).type === 'notification'
    && !!(message as { notification?: unknown }).notification;
}

interface NotificationsState extends RequestState {
  items: AppNotification[];
  unread: number;
  /** The last page loaded, zero based. */
  page: number;
  hasMore: boolean;
  loadingMore: boolean;
}

const initial: NotificationsState = { items: [], unread: 0, page: 0, hasMore: false, loadingMore: false, ...initialRequestState };

/** The signed-in user's notifications. Loads on sign in and takes new ones from the socket. */
export const NotificationsStore = signalStore(
  { providedIn: 'root' },
  withState<NotificationsState>(initial),
  withComputed(({ unread, status }) => ({
    hasUnread: computed(() => unread() > 0),
    loading: computed(() => status() === 'loading'),
  })),
  withMethods((store, api = inject(NotificationsService)) => {
    const setRead = (id: string, readAt: string | null) =>
      patchState(store, { items: store.items().map(item => (item.id === id ? { ...item, readAt } : item)) });

    return {
      async load(): Promise<void> {
        patchState(store, { status: 'loading', error: null });
        try {
          const { page, unread } = await firstValueFrom(forkJoin({ page: api.list(0, NOTIFICATION_PAGE), unread: api.unreadCount() }));
          patchState(store, { items: page.content ?? [], unread, page: 0, hasMore: !page.last, status: 'loaded' });
        } catch (error) {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'Notifications could not be loaded.' });
        }
      },
      async loadMore(): Promise<void> {
        if (!store.hasMore() || store.loadingMore()) return;
        patchState(store, { loadingMore: true });
        try {
          const next = await firstValueFrom(api.list(store.page() + 1, NOTIFICATION_PAGE));
          const known = new Set(store.items().map(item => item.id));
          patchState(store, {
            items: [...store.items(), ...(next.content ?? []).filter(item => !known.has(item.id))],
            page: store.page() + 1,
            hasMore: !next.last,
          });
        } catch (error) {
          patchState(store, { error: errorMessage(error) ?? 'Older notifications could not be loaded.' });
        } finally {
          patchState(store, { loadingMore: false });
        }
      },
      /** A notification pushed over the socket. */
      receive(notification: AppNotification): void {
        if (!notification?.id || store.items().some(item => item.id === notification.id)) return;
        patchState(store, {
          items: [notification, ...store.items()],
          unread: store.unread() + (notification.readAt ? 0 : 1),
        });
      },
      async markRead(id: string): Promise<void> {
        const item = store.items().find(candidate => candidate.id === id);
        if (!item || item.readAt) return;
        setRead(id, new Date().toISOString());
        patchState(store, { unread: Math.max(0, store.unread() - 1) });
        try {
          const saved = await firstValueFrom(api.markRead(id));
          setRead(id, saved?.readAt ?? new Date().toISOString());
        } catch {
          setRead(id, null);
          patchState(store, { unread: store.unread() + 1 });
        }
      },
      async markAllRead(): Promise<void> {
        const before = store.items();
        const unread = store.unread();
        const now = new Date().toISOString();
        patchState(store, { items: before.map(item => (item.readAt ? item : { ...item, readAt: now })), unread: 0 });
        try {
          await firstValueFrom(api.markAllRead());
        } catch (error) {
          patchState(store, { items: before, unread, error: errorMessage(error) ?? 'Notifications could not be marked read.' });
        }
      },
      reset(): void {
        patchState(store, initial);
      },
    };
  }),
  withHooks({
    onInit(store, session = inject(SessionStore), socket = inject(WebsocketService)) {
      effect(() => {
        const userId = session.userId();
        untracked(() => (userId ? store.load() : store.reset()));
      });
      socket.getMessage()
        .pipe(filter(isNotificationMessage), takeUntilDestroyed())
        .subscribe(message => store.receive(message.notification));
    },
  }),
);
