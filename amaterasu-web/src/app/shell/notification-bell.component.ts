import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { Router } from '@angular/router';
import { NotificationsStore } from '../core/stores/notifications.store';
import { AppNotification } from '../models/notification.model';
import { ago } from '../shared/format';
import { EmptyStateComponent } from '../shared/ui/empty-state.component';
import { IconComponent } from '../shared/ui/icon.component';
import { SkeletonComponent } from '../shared/ui/skeleton.component';
import { notificationLook } from './notification-kinds';

/** The top bar bell: a dot while anything is unread, and the latest notifications in a dropdown. */
@Component({
  selector: 'app-notification-bell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, IconComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <button #trigger type="button" class="iconbtn" cdkOverlayOrigin #origin="cdkOverlayOrigin" [attr.aria-label]="label()"
            aria-haspopup="dialog" [attr.aria-expanded]="open()" (click)="toggle()">
      <app-icon name="bell" />
      @if (store.hasUnread()) {
        <span class="pip" aria-hidden="true"></span>
      }
    </button>
    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="origin" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayPositions]="positions" [cdkConnectedOverlayPush]="true"
                 [cdkConnectedOverlayViewportMargin]="8" (overlayOutsideClick)="outside($event)"
                 (overlayKeydown)="onKey($event)" (detach)="open.set(false)">
      <section class="pop" role="dialog" aria-labelledby="notif-h">
        <header class="head">
          <h2 class="h3" id="notif-h">Notifications</h2>
          @if (store.hasUnread()) {
            <span class="tag accent">{{ store.unread() }} unread</span>
            <button type="button" class="btn sm ghost" (click)="store.markAllRead()">Mark all read</button>
          }
        </header>
        <div class="list">
          @if (store.loading() && !store.items().length) {
            <div class="pad"><app-skeleton [count]="3" label="Loading notifications" /></div>
          } @else if (store.error() && !store.items().length) {
            <app-empty-state icon="alert" title="Notifications could not be loaded" [message]="store.error()!">
              <button type="button" class="btn sm" (click)="store.load()">Try again</button>
            </app-empty-state>
          } @else {
            @for (item of store.items(); track item.id) {
              <button type="button" class="item" [class.unread]="!item.readAt" (click)="openItem(item)">
                <span class="feed-ico" [class]="tone(item)"><app-icon [name]="look(item).icon" size="sm" /></span>
                <span class="grow text">
                  <span class="title">{{ item.title }}</span>
                  @if (item.body) {
                    <span class="small dim body">{{ item.body }}</span>
                  }
                  <span class="small muted">{{ when(item.createdAt) }}</span>
                </span>
                @if (!item.readAt) {
                  <span class="unread-dot" role="img" aria-label="Unread"></span>
                }
              </button>
            } @empty {
              <app-empty-state icon="bell" title="Nothing new"
                message="Lab problems, points awarded to you and new questions in your labs show up here." />
            }
          }
        </div>
        @if (store.hasMore()) {
          <footer class="foot">
            <button type="button" class="btn sm ghost block" (click)="store.loadMore()" [disabled]="store.loadingMore()">
              @if (store.loadingMore()) {<app-icon name="spinner" size="sm" [spin]="true" />}Show older
            </button>
          </footer>
        }
      </section>
    </ng-template>
  `,
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');

  protected readonly open = signal(false);
  protected readonly positions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  ];
  protected readonly label = computed(() => {
    const unread = this.store.unread();
    return unread ? `Notifications, ${unread} unread` : 'Notifications';
  });
  protected readonly look = (item: AppNotification) => notificationLook(item.type);
  protected readonly when = ago;

  protected tone(item: AppNotification): string {
    const tone = notificationLook(item.type).tone;
    return tone ? `s-${tone}` : '';
  }

  protected toggle(): void {
    const opening = !this.open();
    this.open.set(opening);
    // The socket can miss messages while it reconnects, so opening rereads the list
    if (opening) this.store.load();
  }

  protected outside(event: MouseEvent): void {
    if (!this.trigger()?.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.open.set(false);
      this.trigger()?.nativeElement.focus();
    }
  }

  protected openItem(item: AppNotification): void {
    this.store.markRead(item.id);
    this.open.set(false);
    if (item.link) {
      this.router.navigateByUrl(item.link);
    }
  }
}
