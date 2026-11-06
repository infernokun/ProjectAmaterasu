import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { NotificationsService } from '../core/services/notifications.service';
import { AppNotification } from '../models/notification.model';
import { AuthService } from '../services/auth.service';
import { WebsocketService } from '../services/websocket.service';
import { FakeAuthService } from '../testing/fake-auth';
import { NotificationBellComponent } from './notification-bell.component';

describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;
  let api: jasmine.SpyObj<NotificationsService>;
  let router: Router;
  let items: AppNotification[];

  beforeEach(async () => {
    items = [
      { id: 'n1', type: 'POINTS_AWARDED', title: '+50 pts in Winter Range', body: 'Writeup bonus · from dkim', link: '/rooms/r1/scoreboard/jmorales',
        createdAt: new Date().toISOString(), readAt: null },
      { id: 'n2', type: 'LAB_FAILED', title: 'Log Hunting failed to start', createdAt: new Date().toISOString(), readAt: new Date().toISOString() },
    ];
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1', username: 'jmorales' }, 'MEMBER');
    api = jasmine.createSpyObj<NotificationsService>('NotificationsService', ['list', 'unreadCount', 'markRead', 'markAllRead']);
    api.list.and.callFake(() => of({ content: items, totalElements: 2, totalPages: 1, number: 0, size: 20, first: true, last: true }));
    api.unreadCount.and.callFake(() => of(items.filter(item => !item.readAt).length));
    api.markRead.and.callFake(id => of({ ...items.find(item => item.id === id)!, readAt: new Date().toISOString() }));
    api.markAllRead.and.returnValue(of(1));
    TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificationsService, useValue: api },
        { provide: WebsocketService, useValue: { getMessage: () => EMPTY } },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    await settle();
  });

  afterEach(() => fixture.destroy());

  async function settle() {
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
  }

  const bell = () => fixture.nativeElement.querySelector('button.iconbtn') as HTMLButtonElement;
  const panel = () => document.querySelector('.cdk-overlay-container .pop') as HTMLElement | null;

  it('shows a dot and the unread count in its label', () => {
    expect(fixture.nativeElement.querySelector('.pip')).toBeTruthy();
    expect(bell().getAttribute('aria-label')).toBe('Notifications, 1 unread');
  });

  it('lists notifications with the unread ones marked', async () => {
    bell().click();
    await settle();
    const rows = Array.from(panel()!.querySelectorAll('.item'));
    expect(rows.length).toBe(2);
    expect(rows[0].classList).toContain('unread');
    expect(rows[0].textContent).toContain('Writeup bonus · from dkim');
    expect(rows[1].classList).not.toContain('unread');
  });

  it('opens a notification, marks it read and follows its link', async () => {
    bell().click();
    await settle();
    (panel()!.querySelector('.item') as HTMLButtonElement).click();
    await settle();
    expect(api.markRead).toHaveBeenCalledWith('n1');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/rooms/r1/scoreboard/jmorales');
    expect(panel()).toBeNull();
    expect(fixture.nativeElement.querySelector('.pip')).toBeNull();
  });

  it('marks everything read', async () => {
    bell().click();
    await settle();
    const button = Array.from(panel()!.querySelectorAll('button')).find(b => b.textContent?.includes('Mark all read'))!;
    button.click();
    await settle();
    expect(api.markAllRead).toHaveBeenCalled();
    expect(bell().getAttribute('aria-label')).toBe('Notifications');
  });

  it('says when there is nothing', async () => {
    items = [];
    bell().click();
    await settle();
    expect(panel()!.textContent).toContain('Nothing new');
  });
});
